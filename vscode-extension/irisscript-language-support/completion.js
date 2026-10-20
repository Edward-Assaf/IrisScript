const vscode = require("vscode");

/**
 * Keywords recognized by IrisScript.
 *
 * Keep this list synchronized with the compiler's lexer/parser.
 * Remove any entries that are not actual IrisScript keywords.
 */
const KEYWORDS = [
    "boolean",
    "elseif",
    "integer",
    "true",
    "break",
    "false",
    "return",
    "variable",
    "constant",
    "function",
    "skip",
    "void",
    "decimal",
    "global",
    "terminal",
    "while"
];

/**
 * Collect declarations from a source file.
 *
 * This is an initial implementation based on regular expressions.
 * It recognizes the declaration forms:
 *
 *     variable playerScore integer = 100;
 *     function calculateScore() { ... }
 *
 * It does not yet handle comments, strings, nested scopes,
 * overloads, imports, or every possible declaration form.
 *
 * @param {string} source
 * @param {vscode.Uri} uri
 * @returns {Array<{
 *   name: string,
 *   kind: vscode.CompletionItemKind,
 *   detail: string,
 *   uri: vscode.Uri
 * }>}
 */
function collectDeclaredNames(source, uri) {
    const declarations = [];
    const seen = new Set();

    function add(name, kind, detail) {
        if (seen.has(name)) {
            return;
        }

        seen.add(name);

        declarations.push({
            name,
            kind,
            detail,
            uri
        });
    }

    // Example:
    // variable playerScore integer = 100;
    //
    // Captures:
    //   group 1: playerScore
    //   group 2: integer
    const variableRegex =
        /\bvariable\s+([A-Za-z_]\w*)\s+([A-Za-z_]\w*)/g;

    for (const match of source.matchAll(variableRegex)) {
        add(
            match[1],
            vscode.CompletionItemKind.Variable,
            `IrisScript variable — type: ${match[2]}`
        );
    }

    // Example:
    // function calculateScore() { ... }
    const functionRegex =
        /\bfunction\s+([A-Za-z_]\w*)\s+([A-Za-z_]\w*)\s*\(/g;

    for (const match of source.matchAll(functionRegex)) {
        add(
            match[1],
            vscode.CompletionItemKind.Function,
            `IrisScript function - return type: ${match[2]}`
        );
    }

    return declarations;
}

/**
 * Create completion items for language keywords.
 *
 * @returns {vscode.CompletionItem[]}
 */
function createKeywordCompletions() {
    return KEYWORDS.map((keyword) => {
        const item = new vscode.CompletionItem(
            keyword,
            vscode.CompletionItemKind.Keyword
        );

        item.detail = "IrisScript keyword";
        item.sortText = `1_${keyword}`;

        return item;
    });
}

/**
 * Register the IrisScript completion provider.
 *
 * Searches all workspace .iris files for declarations and includes
 * the current editor's unsaved contents.
 *
 * @param {vscode.ExtensionContext} context
 */
function registerCompletionProvider(context) {
    const provider = vscode.languages.registerCompletionItemProvider(
        { language: "irisscript", scheme: "file" },

        {
            async provideCompletionItems(document, position) {
                // Only offer completions for IrisScript source files.
                if (!document.fileName.endsWith(".iris")) {
                    return [];
                }

                // Determine the prefix immediately before the cursor.
                // This avoids accidentally including text after the cursor.
                const linePrefix = document
                    .lineAt(position.line)
                    .text.slice(0, position.character);

                const prefixMatch =
                    linePrefix.match(/[A-Za-z_]\w*$/);

                const prefix = prefixMatch ? prefixMatch[0] : "";

                const replacementRange = new vscode.Range(
                    position.line,
                    position.character - prefix.length,
                    position.line,
                    position.character
                );

                const items = createKeywordCompletions();

                // Find all IrisScript files in the current workspace.
                const files = await vscode.workspace.findFiles(
                    "**/*.iris",
                    "**/{node_modules,.git,target,build}/**");

                // Use a Map so that each URI occurs only once.
                // The current editor is inserted first using its
                // unsaved buffer rather than reading its disk version.
                const sources = new Map();

                sources.set(document.uri.toString(), {
                    text: document.getText(),
                    uri: document.uri
                });

                for (const uri of files) {
                    const key = uri.toString();

                    if (sources.has(key)) {
                        continue;
                    }

                    try {
                        const otherDocument =
                            await vscode.workspace.openTextDocument(uri);

                        sources.set(key, {
                            text: otherDocument.getText(),
                            uri
                        });
                    } catch (error) {
                        // A file that cannot be opened should not prevent
                        // completion from working for the remaining files.
                    }
                }

                // Gather declarations from all source files.
                const declarations = new Map();

                for (const source of sources.values()) {
                    const found = collectDeclaredNames(
                        source.text,
                        source.uri
                    );

                    for (const declaration of found) {
                        // Only show names matching what the user typed.
                        if (
                            prefix.length > 0 &&
                            !declaration.name
                                .toLowerCase()
                                .startsWith(prefix.toLowerCase())
                        ) {
                            continue;
                        }

                        // Deduplicate names across the workspace.
                        // The first declaration found is retained.
                        if (!declarations.has(declaration.name)) {
                            declarations.set(
                                declaration.name,
                                declaration
                            );
                        }
                    }
                }

                // Convert declarations to VS Code completion items.
                for (const declaration of declarations.values()) {
                    const item = new vscode.CompletionItem(
                        declaration.name,
                        declaration.kind
                    );

                    item.detail = declaration.detail;
                    item.sortText = `0${declaration.name}`;
                    item.range = replacementRange;

                    items.push(item);
                }

                // Set the replacement range on keyword suggestions too.
                for (const item of items) {
                    if (!item.range) {
                        item.range = replacementRange;
                    }
                }

                return items;
            }
        },

        // Characters that trigger completion automatically.
        ..."_abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    );

    context.subscriptions.push(provider);
}

module.exports = {
    KEYWORDS,
    collectDeclaredNames,
    registerCompletionProvider
};