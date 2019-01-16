package org.ole.planet.myplanet.model;

import java.util.List;

public class DocumentResponse {
    private String total_rows;

    private String offset;

    private List<Rows> rows;

    public String getTotal_rows() {
        return total_rows;
    }

    public void setTotal_rows(String total_rows) {
        this.total_rows = total_rows;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }

    public List<Rows> getRows() {
        return rows;
    }

    public void setRows(List<Rows> rows) {
        this.rows = rows;
    }

    @Override
    public String toString() {
        return "ClassPojo [total_rows = " + total_rows + ", offset = " + offset + ", rows = " + rows + "]";
    }
}
