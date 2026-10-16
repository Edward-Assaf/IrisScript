package irisscript.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SourceFile {

    private final int id;

    private final String name;

    private final String contents;

    private final int contentsLength;

    private final int[] lineOffsets;

    public SourceFile(int id, String name, String contents) {
        this.id = id;
        this.name = name;
        this.contents = contents;
        this.contentsLength = contents.length();
        this.lineOffsets = buildLineOffsets();
    }

    public int getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContents() {
        return contents;
    }

    public int getContentsLength() {
        return contentsLength;
    }

    public int getLinesCount() {
        return lineOffsets.length;
    }

    public int[] getLineOffsets() {
        return lineOffsets.clone();
    }

    public SourcePosition getPosition(int offset) {
        validateOffset(offset);
        return new SourcePosition(id, offset);
    }

    public SourceSpan getSpan(int startOffset, int endOffset) {
        validateOffset(startOffset);
        validateOffset(endOffset);
        return new SourceSpan(id, startOffset, endOffset);
    }

    public int getLineNumber(int offset) {
        validateOffset(offset);
        return findLineIndex(offset) + 1;
    }

    public int getColumnNumber(int offset) {
        validateOffset(offset);
        int lineIndex = findLineIndex(offset);
        return offset - lineOffsets[lineIndex] + 1;
    }

    public String getText(SourceSpan span) {
        validateSpan(span);
        return contents.substring(span.startOffset(), span.endOffset());
    }

    public String getText(int lineNumber) {
        if (lineNumber < 1 || lineNumber > lineOffsets.length) {
            throw new IllegalArgumentException("lineNumber is out of the bounds of the lineOffsets array");
        }
        int start = lineOffsets[lineNumber - 1];
        int end = (lineNumber < lineOffsets.length) ? lineOffsets[lineNumber] : contentsLength;
        if (end > start && contents.charAt(end - 1) == '\n') {
            end--;
        }
        if (end > start && contents.charAt(end - 1) == '\r') {
            end--;
        }
        return contents.substring(start, end);
    }

    private int[] buildLineOffsets() {
        List<Integer> tempLineOffsets = new ArrayList<>();
        tempLineOffsets.add(0);
        for (int i = 0; i < contentsLength; i++) {
            char current = contents.charAt(i);
            if (current == '\r') {
                if (i + 1 < contentsLength && contents.charAt(i + 1) == '\n') {
                    i++;
                }
                tempLineOffsets.add(i + 1);
            } else if (current == '\n') {
                tempLineOffsets.add(i + 1);
            }
        }
        int[] result = new int[tempLineOffsets.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = tempLineOffsets.get(i);
        }
        return result;
    }

    private void validateOffset(int offset) {
        if (offset < 0 || offset > contentsLength) {
            throw new IllegalArgumentException("offset is out of the bounds of the source file contents");
        }
    }

    private void validateSpan(SourceSpan span) {
        Objects.requireNonNull(span, "span must not be null");
        validateOffset(span.startOffset());
        validateOffset(span.endOffset());
    }

    private int findLineIndex(int offset) {
        int start = 0, end = lineOffsets.length - 1;
        while (start <= end) {
            int mid = start + (end - start) / 2;
            if (lineOffsets[mid] <= offset) {
                start = mid + 1;
            } else {
                end = mid - 1;
            }
        }
        return end;
    }

}