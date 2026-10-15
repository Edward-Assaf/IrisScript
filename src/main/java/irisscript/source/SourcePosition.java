package irisscript.source;

final record SourcePosition(int sourceID, int offset) {

    public SourcePosition {
        if (sourceID < 0) {
            throw new IllegalArgumentException("sourceID cannot be negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
    }

}