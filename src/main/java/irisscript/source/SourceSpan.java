package irisscript.source;

public final record SourceSpan(int sourceID, int startOffset, int endOffset) {

    public SourceSpan {
        if (sourceID < 0) {
            throw new IllegalArgumentException("sourceID cannot be negative");
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset cannot be negative");
        }
        if (endOffset < 0) {
            throw new IllegalArgumentException("endOffset cannot be negative");
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException("endOffset cannot precede startOffset");
        }
    }

    public SourcePosition startPosition() {
        return new SourcePosition(sourceID, startOffset);
    }

    public SourcePosition endPosition() {
        return new SourcePosition(sourceID, endOffset);
    }

    public int length() {
        return endOffset - startOffset;
    }

    public boolean isEmpty() {
        return startOffset == endOffset;
    }

    public boolean contains(int offset) {
        return offset >= startOffset && offset < endOffset;
    }

    public static SourceSpan between(SourcePosition start, SourcePosition end) {
        if (start.sourceID() != end.sourceID()) {
            throw new IllegalArgumentException("Cannot create a span accross different source files");
        }
        return new SourceSpan(start.sourceID(), start.offset(), end.offset());
    }

    public static SourceSpan covering(SourceSpan start, SourceSpan end) {
        if (start.sourceID() != end.sourceID()) {
            throw new IllegalArgumentException("Cannot combine spans from different source files");
        }
        return new SourceSpan(start.sourceID, Math.min(start.startOffset, end.startOffset),
                Math.max(start.endOffset, end.endOffset));
    }

}