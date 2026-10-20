const vscode = require('vscode');
const cp = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const {
	registerCompletionProvider
} = require("./completion");

/**
 * Resolves the execution target for IrisScript.
 * 1. User config: irisscript.compilerPath
 * 2. Bundled compiler JAR: bin/iris.jar (inside extension)
 * 3. Local repo script: iris.bat / iris
 * 4. System PATH: iris.bat / iris
 */
function resolveCompiler(documentUri) {
	const configPath = vscode.workspace.getConfiguration('irisscript').get('compilerPath');
	if (configPath && fs.existsSync(configPath)) {
		if (configPath.endsWith('.jar')) {
			return { type: 'jar', path: configPath };
		}
		return { type: 'script', path: configPath };
	}

	// 2. Bundled standalone JAR inside the extension package
	const bundledJar = path.join(__dirname, 'bin', 'iris.jar');
	if (fs.existsSync(bundledJar)) {
		return { type: 'jar', path: bundledJar };
	}

	const isWindows = process.platform === 'win32';
	const scriptName = isWindows ? 'iris.bat' : 'iris';

	// 3. Walk up from document file path
	if (documentUri && documentUri.fsPath) {
		let currentDir = path.dirname(documentUri.fsPath);
		for (let i = 0; i < 5; i++) {
			const candidate = path.join(currentDir, scriptName);
			if (fs.existsSync(candidate)) {
				return { type: 'script', path: candidate };
			}
			const subCandidate = path.join(currentDir, 'irisscript-language', scriptName);
			if (fs.existsSync(subCandidate)) {
				return { type: 'script', path: subCandidate };
			}
			const parent = path.dirname(currentDir);
			if (parent === currentDir) break;
			currentDir = parent;
		}
	}

	// 4. Search in workspace folders
	if (vscode.workspace.workspaceFolders) {
		for (const folder of vscode.workspace.workspaceFolders) {
			const root = folder.uri.fsPath;
			const candidates = [
				path.join(root, scriptName),
				path.join(root, 'irisscript-language', scriptName),
				path.join(root, '..', scriptName),
				path.join(root, '..', 'irisscript-language', scriptName)
			];
			for (const candidate of candidates) {
				if (fs.existsSync(candidate)) {
					return { type: 'script', path: candidate };
				}
			}
		}
	}

	// 5. Default to system PATH
	return { type: 'script', path: scriptName };
}

/**
 * Builds command string for terminal execution
 */
function buildTerminalCommand(compiler, subCommand, filePaths) {
	const sourceArguments = filePaths.map(filePath => `"${filePath}"`).join(' ');

	if (compiler.type === 'jar') {
		return `java -jar "${compiler.path}" ${subCommand} ${sourceArguments}`;
	}

	return `${getShellPrefix()}"${compiler.path}" ${subCommand} ${sourceArguments}`;
}

/**
 * Returns invocation prefix for PowerShell on Windows
 */
function getShellPrefix() {
	if (process.platform === 'win32') {
		const isCmd = vscode.env.shell && /cmd\.exe/i.test(vscode.env.shell);
		if (!isCmd) {
			return '& ';
		}
	}
	return '';
}

/**
 * Strips ANSI color/escape codes emitted by terminal output
 */
function stripAnsi(str) {
	return str.replace(/\u001B\[[0-9;]*m/g, '');
}

function isIrisDocument(document) {
	return document.languageId === 'irisscript'
		&& !document.isUntitled
		&& document.uri.scheme === 'file'
		&& document.uri.fsPath.toLowerCase().endsWith('.iris');
}

async function getProjectUris(preferredUri) {
	const files = new Map();

	const discovered = await vscode.workspace.findFiles(
		'**/*.iris',
		'**/{node_modules,.git,target,build}/**'
	);

	for (const uri of discovered) {
		files.set(uri.toString(), uri);
	}

	// Include open documents, even when their contents are unsaved.
	for (const document of vscode.workspace.textDocuments) {
		if (isIrisDocument(document)) {
			files.set(document.uri.toString(), document.uri);
		}
	}

	// Include the triggering file if it was not returned by findFiles.
	if (preferredUri
		&& preferredUri.scheme === 'file'
		&& preferredUri.fsPath.toLowerCase().endsWith('.iris')) {
		files.set(preferredUri.toString(), preferredUri);
	}

	return [...files.values()].sort(
		(a, b) => a.fsPath.localeCompare(b.fsPath)
	);
}

async function collectDiagnosticSources(uris, tempRoot) {
	const sources = [];
	const compilerPathToUri = new Map();

	for (let i = 0; i < uris.length; i++) {
		const uri = uris[i];

		const document = vscode.workspace.textDocuments.find(
			doc => doc.uri.toString() === uri.toString()
		);

		let text;

		if (document) {
			text = document.getText();
		} else {
			try {
				text = fs.readFileSync(uri.fsPath, 'utf8');
			} catch {
				// The file may have been deleted since discovery.
				continue;
			}
		}

		let compilerPath = uri.fsPath;

		if (document && document.isDirty) {
			// Give each temporary source a unique path. This avoids
			// collisions when different directories contain the same name.
			compilerPath = path.join(
				tempRoot,
				'sources',
				String(i),
				path.basename(uri.fsPath)
			);

			fs.mkdirSync(path.dirname(compilerPath), {
				recursive: true
			});

			fs.writeFileSync(compilerPath, text, 'utf8');
		}

		const source = {
			uri,
			text,
			compilerPath
		};

		sources.push(source);

		compilerPathToUri.set(
			normalizedPath(compilerPath),
			uri
		);
	}

	return {
		sources,
		compilerPathToUri
	};
}

function parseProjectDiagnostics(output, sourceInfo) {
	const diagnosticsByUri = new Map(
		sourceInfo.sources.map(source => [
			source.uri.toString(),
			[]
		])
	);

	const lines = stripAnsi(output).split(/\r?\n/);

	// Greedy path matching also accommodates Windows drive-letter colons.
	const headerPattern =
		/^(.+):(\d+):(\d+):\s*(error|warning|info)\[([^\]]+)\]:\s*(.*)$/;

	for (let i = 0; i < lines.length; i++) {
		const match = lines[i].match(headerPattern);
		if (!match) continue;

		const [
			,
			reportedPath,
			lineText,
			columnText,
			severityText,
			code,
			message
		] = match;

		const uri = sourceInfo.compilerPathToUri.get(
			normalizedPath(reportedPath)
		);

		// Never attach an unidentifiable diagnostic to the active editor.
		if (!uri) {
			console.warn(
				`Could not map IrisScript diagnostic path: ${reportedPath}`
			);
			continue;
		}

		const source = sourceInfo.sources.find(
			item => item.uri.toString() === uri.toString()
		);

		if (!source) continue;

		const sourceLines = source.text.split(/\r?\n/);

		const line = Math.max(
			0,
			Math.min(Number(lineText) - 1, sourceLines.length - 1)
		);

		const text = sourceLines[line] || '';

		const column = Math.max(
			0,
			Math.min(Number(columnText) - 1, text.length)
		);

		// ANSIFormatter prints the source line followed by its caret line.
		// Recover the actual span instead of underlining the entire line.
		const caretLine = lines[i + 2] || '';
		const caretMatch = caretLine.match(/^\s*\|\s*(\^+)/);

		let length = caretMatch ? caretMatch[1].length : 0;

		// Fallback for compiler output that lacks a caret excerpt.
		if (length === 0) {
			const word = text.slice(column).match(/^[A-Za-z_]\w*/);
			length = word ? word[0].length : 1;
		}

		const endColumn = Math.min(
			text.length,
			column + Math.max(1, length)
		);

		const range = new vscode.Range(
			line,
			column,
			line,
			Math.max(column, endColumn)
		);

		let severity = vscode.DiagnosticSeverity.Error;

		if (severityText.toLowerCase() === 'warning') {
			severity = vscode.DiagnosticSeverity.Warning;
		} else if (severityText.toLowerCase() === 'info') {
			severity = vscode.DiagnosticSeverity.Information;
		}

		const diagnostic = new vscode.Diagnostic(
			range,
			`[${code}] ${message}`,
			severity
		);

		diagnostic.source = 'irisscript';
		diagnostic.code = code;

		diagnosticsByUri.get(uri.toString()).push(diagnostic);
	}

	return diagnosticsByUri;
}

function normalizedPath(filePath) {
	const resolved = path.normalize(path.resolve(filePath));
	return process.platform === 'win32'
		? resolved.toLowerCase()
		: resolved;
}

async function runProjectDiagnostics(
	preferredUri,
	diagnosticCollection,
	runId,
	isCurrentRun
) {
	const uris = await getProjectUris(preferredUri);

	if (!isCurrentRun(runId)) return;

	if (uris.length === 0) {
		diagnosticCollection.clear();
		return;
	}

	const tempRoot = fs.mkdtempSync(
		path.join(os.tmpdir(), 'irisscript-diagnostics-')
	);

	try {
		const sourceInfo = await collectDiagnosticSources(
			uris,
			tempRoot
		);

		if (!isCurrentRun(runId) || sourceInfo.sources.length === 0) {
			return;
		}

		const compiler = resolveCompiler(
			preferredUri || sourceInfo.sources[0].uri
		);

		const outputDirectory = path.join(tempRoot, 'out');

		const sourcePaths = sourceInfo.sources.map(
			source => source.compilerPath
		);

		const command = compiler.type === 'jar'
			? `java -jar "${compiler.path}" build -nc -o "${outputDirectory}" ${sourcePaths.map(p => `"${p}"`).join(' ')}`
			: `${getShellPrefix()} "${compiler.path}" build -nc -o "${outputDirectory}" ${sourcePaths.map(p => `"${p}"`).join(' ')}`;

		const cwd = path.dirname(sourceInfo.sources[0].uri.fsPath);

		await new Promise(resolve => {
			cp.exec(
				command,
				{
					cwd,
					windowsHide: true,
					maxBuffer: 10 * 1024 * 1024
				},
				(error, stdout, stderr) => {
					if (!isCurrentRun(runId)) {
						resolve();
						return;
					}

					const output = `${stdout || ''}\n${stderr || ''}`;

					const parsed = parseProjectDiagnostics(
						output,
						sourceInfo
					);

					// Clear stale results, including results for deleted files.
					diagnosticCollection.clear();

					// Set results for EVERY project source, even if it has
					// no errors and even if its editor is not currently open.
					for (const source of sourceInfo.sources) {
						diagnosticCollection.set(
							source.uri,
							parsed.get(source.uri.toString()) || []
						);
					}

					if (error && !output.trim()) {
						vscode.window.showErrorMessage(
							`IrisScript diagnostics could not run: ${error.message}`
						);
					}

					resolve();
				}
			);
		});
	} catch (error) {
		if (isCurrentRun(runId)) {
			vscode.window.showErrorMessage(
				`IrisScript diagnostics failed: ${error.message}`
			);
		}
	} finally {
		// The compiler has finished, so temporary source copies can be removed.
		try {
			fs.rmSync(tempRoot, {
				recursive: true,
				force: true
			});
		} catch {
			// Temporary-file cleanup is best effort.
		}
	}
}

function activate(context) {
	// ------------------------------------------------------------
	// 1. Diagnostics and completion
	// ------------------------------------------------------------

	const diagnosticCollection =
		vscode.languages.createDiagnosticCollection('irisscript');

	context.subscriptions.push(diagnosticCollection);

	registerCompletionProvider(context);

	// ------------------------------------------------------------
	// 2. Shared state
	// ------------------------------------------------------------

	let activeTerminal = null;
	let diagnosticRunId = 0;
	let debounceTimer = null;

	const isCurrentRun = runId => runId === diagnosticRunId;

	// ------------------------------------------------------------
	// 3. Schedule project-wide diagnostics
	// ------------------------------------------------------------

	function scheduleProjectDiagnostics(preferredUri, delay = 350) {
		diagnosticRunId++;

		const runId = diagnosticRunId;

		if (debounceTimer !== null) {
			clearTimeout(debounceTimer);
		}

		debounceTimer = setTimeout(() => {
			debounceTimer = null;

			runProjectDiagnostics(
				preferredUri,
				diagnosticCollection,
				runId,
				isCurrentRun
			);
		}, delay);
	}

	// ------------------------------------------------------------
	// 4. Run/build all IrisScript files in the workspace
	// ------------------------------------------------------------

	async function executeProjectCommand(subCommand, targetUri) {
		const uris = await getProjectUris(targetUri);

		if (uris.length === 0) {
			vscode.window.showWarningMessage(
				'No IrisScript files found in the workspace.'
			);
			return;
		}

		// Save modified IrisScript documents before running the compiler.
		const projectUris = new Set(
			uris.map(uri => uri.toString())
		);

		for (const document of vscode.workspace.textDocuments) {
			if (
				projectUris.has(document.uri.toString()) &&
				document.isDirty
			) {
				const saved = await document.save();

				if (!saved) {
					vscode.window.showErrorMessage(
						'Could not save ${ document.uri.fsPath }.' +
						'Save it and try again.'
					);
					return;
				}
			}
		}

		const compiler = resolveCompiler(targetUri || uris[0]);

		if (
			!activeTerminal ||
			activeTerminal.exitStatus !== undefined
		) {
			activeTerminal =
				vscode.window.createTerminal('IrisScript');
		}

		activeTerminal.show(true);

		activeTerminal.sendText(
			buildTerminalCommand(
				compiler,
				subCommand,
				uris.map(uri => uri.fsPath)
			)
		);
	}

	// ------------------------------------------------------------
	// 5. Register Run command
	// ------------------------------------------------------------

	const runDisposable = vscode.commands.registerCommand(
		'irisscript.runFile',
		async uri => {
			const targetUri =
				uri ||
				vscode.window.activeTextEditor?.document.uri;

			if (!targetUri) {
				vscode.window.showWarningMessage(
					'No active IrisScript file to run.'
				);
				return;
			}

			await executeProjectCommand('run', targetUri);
		}
	);

	// ------------------------------------------------------------
	// 6. Register Build command
	// ------------------------------------------------------------

	const buildDisposable = vscode.commands.registerCommand(
		'irisscript.buildFile',
		async uri => {
			const targetUri = uri ||
				vscode.window.activeTextEditor?.document.uri;

			if (!targetUri) {
				vscode.window.showWarningMessage(
					'No active IrisScript file to build.'
				);
				return;
			}

			await executeProjectCommand('build', targetUri);
		}
	);

	context.subscriptions.push(
		runDisposable,
		buildDisposable
	);

	// ------------------------------------------------------------
	// 7. React to edits, saves, and opened documents
	// ------------------------------------------------------------

	context.subscriptions.push(
		vscode.workspace.onDidChangeTextDocument(event => {
			if (isIrisDocument(event.document)) {
				scheduleProjectDiagnostics(event.document.uri);
			}
		}),

		vscode.workspace.onDidSaveTextDocument(document => {
			if (isIrisDocument(document)) {
				scheduleProjectDiagnostics(document.uri, 0);
			}
		}),

		vscode.workspace.onDidOpenTextDocument(document => {
			if (isIrisDocument(document)) {
				scheduleProjectDiagnostics(document.uri, 0);
			}
		}),

		vscode.window.onDidChangeActiveTextEditor(editor => {
			if (editor && isIrisDocument(editor.document)) {
				scheduleProjectDiagnostics(
					editor.document.uri,
					0
				);
			}
		})
	);

	// ------------------------------------------------------------
	// 8. React to external file changes
	// ------------------------------------------------------------

	const watcher =
		vscode.workspace.createFileSystemWatcher('**/*.iris');

	context.subscriptions.push(
		watcher,

		watcher.onDidCreate(uri => {
			scheduleProjectDiagnostics(uri, 0);
		}),

		watcher.onDidChange(uri => {
			scheduleProjectDiagnostics(uri, 0);
		}),

		watcher.onDidDelete(uri => {
			scheduleProjectDiagnostics(uri, 0);
		})
	);

	// ------------------------------------------------------------
	// 9. Initial project-wide diagnostics
	// ------------------------------------------------------------

	scheduleProjectDiagnostics(
		vscode.window.activeTextEditor?.document.uri,
		0
	);
}

function deactivate() { }

module.exports = {
	activate,
	deactivate
};