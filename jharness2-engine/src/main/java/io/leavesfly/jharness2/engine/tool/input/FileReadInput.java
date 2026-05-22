package io.leavesfly.jharness2.engine.tool.input;

public class FileReadInput {
    private String file_path;
    private Integer offset;
    private Integer limit;

    public String getFile_path() { return file_path; }
    public void setFile_path(String file_path) { this.file_path = file_path; }
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
