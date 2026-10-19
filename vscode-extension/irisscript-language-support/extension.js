const vscode = require('vscode');
const cp = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');

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
function buildTerminalCommand(compiler, subCommand, filePath) {
	if (compiler.type === 'jar') {
		return `java -jar "${compiler.path}" ${subCommand} "${filePath}"`;
	}
	return `${getShellPrefix()}"${compiler.path}" ${subCommand} "${filePath}"`;
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

/**
 * Parses compiler output and updates VS Code Diagnostics
 */
function runDiagnostics(document, diagnosticCollection) {
	if (document.languageId !== 'irisscript' || document.isUntitled) {
		return;
	}

	const filePath = document.uri.fsPath;
	const compiler = resolveCompiler(document.uri);
	const cwd = path.dirname(filePath);

	// When unsaved changes exist in memory, write to a temp file so the compiler checks live code
	let targetPath = filePath;
	let isTemp = false;
	const tempDir = path.join(os.tmpdir(), 'irisscript_diagnostics');

	try {
		if (document.isDirty) {
			if (!fs.existsSync(tempDir)) {
				fs.mkdirSync(tempDir, { recursive: true });
			}
			targetPath = path.join(tempDir, path.basename(filePath));
			fs.writeFileSync(targetPath, document.getText(), 'utf8');
			isTemp = true;
		}
	} catch {
		targetPath = filePath;
		isTemp = false;
	}

	const tempOut = path.join(tempDir, 'out');
	const command = compiler.type === 'jar'
		? `java -jar "${compiler.path}" build -nc -o "${tempOut}" "${targetPath}"`
		: `"${compiler.path}" build -nc -o "${tempOut}" "${targetPath}"`;

	cp.exec(command, { cwd }, (error, stdout, stderr) => {
		if (isTemp) {
			try {
				if (fs.existsSync(targetPath)) {
					fs.unlinkSync(targetPath);
				}
			} catch {}
		}

		const rawOutput = (stdout || '') + '\n' + (stderr || '');
		const output = stripAnsi(rawOutput);

		const diagnostics = [];
		// Regex matches: filename:line:col: severity[CODE]: message
		// Example: hello.iris:10:5: error[E2001]: Variable 'x' is not declared
		const regex = /^(.+?):(\d+):(\d+):\s*(error|warning|info)\[([^\]]+)\]:\s*(.*)$/gm;
		let match;

		while ((match = regex.exec(output)) !== null) {
			const [, , lineStr, colStr, severityStr, code, message] = match;
			const rawLine = Math.max(0, parseInt(lineStr, 10) - 1);
			const line = Math.min(rawLine, Math.max(0, document.lineCount - 1));
			const docLine = document.lineAt(line);
			const col = Math.min(Math.max(0, parseInt(colStr, 10) - 1), docLine.text.length);

			let severity = vscode.DiagnosticSeverity.Error;
			if (severityStr.toLowerCase() === 'warning') {
				severity = vscode.DiagnosticSeverity.Warning;
			} else if (severityStr.toLowerCase() === 'info') {
				severity = vscode.DiagnosticSeverity.Information;
			}

			// Highlight from column to word end or line end
			const endCol = Math.max(col + 1, docLine.text.length);
			const range = new vscode.Range(line, col, line, endCol);

			const diag = new vscode.Diagnostic(range, `[${code}] ${message}`, severity);
			diag.source = 'irisscript';
			diag.code = code;
			diagnostics.push(diag);
		}

		diagnosticCollection.set(document.uri, diagnostics);
	});
}

function activate(context) {
	const diagnosticCollection = vscode.languages.createDiagnosticCollection('irisscript');
	context.subscriptions.push(diagnosticCollection);

	let activeTerminal = null;

	// --- 1. Run Command (Editor Title Run Button) ---
	const runDisposable = vscode.commands.registerCommand('irisscript.runFile', async (uri) => {
		let targetUri = uri;
		if (!targetUri && vscode.window.activeTextEditor) {
			targetUri = vscode.window.activeTextEditor.document.uri;
		}

		if (!targetUri) {
			vscode.window.showWarningMessage('No active IrisScript file to run.');
			return;
		}

		const editor = vscode.window.activeTextEditor;
		if (editor && editor.document.isDirty) {
			await editor.document.save();
		}

		const filePath = targetUri.fsPath;
		const compiler = resolveCompiler(targetUri);

		if (!activeTerminal || activeTerminal.exitStatus !== undefined) {
			activeTerminal = vscode.window.createTerminal('IrisScript');
		}

		activeTerminal.show(true);
		activeTerminal.sendText(buildTerminalCommand(compiler, 'run', filePath));
	});

	// --- 2. Build / Compile Command ---
	const buildDisposable = vscode.commands.registerCommand('irisscript.buildFile', async (uri) => {
		let targetUri = uri;
		if (!targetUri && vscode.window.activeTextEditor) {
			targetUri = vscode.window.activeTextEditor.document.uri;
		}

		if (!targetUri) return;

		const editor = vscode.window.activeTextEditor;
		if (editor && editor.document.isDirty) {
			await editor.document.save();
		}

		const filePath = targetUri.fsPath;
		const compiler = resolveCompiler(targetUri);

		if (!activeTerminal || activeTerminal.exitStatus !== undefined) {
			activeTerminal = vscode.window.createTerminal('IrisScript');
		}

		activeTerminal.show(true);
		activeTerminal.sendText(buildTerminalCommand(compiler, 'build', filePath));
	});

	// --- 3. Error Highlighting Triggers ---
	if (vscode.window.activeTextEditor) {
		runDiagnostics(vscode.window.activeTextEditor.document, diagnosticCollection);
	}

	let debounceTimer = null;

	context.subscriptions.push(
		// Live error checking as the user types (debounced by 350ms)
		vscode.workspace.onDidChangeTextDocument(event => {
			if (event.document.languageId === 'irisscript') {
				if (debounceTimer) {
					clearTimeout(debounceTimer);
				}
				debounceTimer = setTimeout(() => {
					runDiagnostics(event.document, diagnosticCollection);
				}, 350);
			}
		}),
		vscode.window.onDidChangeActiveTextEditor(editor => {
			if (editor) {
				runDiagnostics(editor.document, diagnosticCollection);
			}
		}),
		vscode.workspace.onDidSaveTextDocument(doc => {
			runDiagnostics(doc, diagnosticCollection);
		}),
		vscode.workspace.onDidCloseTextDocument(doc => {
			diagnosticCollection.delete(doc.uri);
		}),
		runDisposable,
		buildDisposable
	);
}

function deactivate() { }

module.exports = {
	activate,
	deactivate
};