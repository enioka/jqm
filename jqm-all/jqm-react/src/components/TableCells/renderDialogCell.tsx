import { Tooltip, Typography } from "@material-ui/core";
import React from "react";

export const renderDialogCell = (
    editingRowId: number | null,
    hint: string,
    currentValue: any,
    printContent: Function,
    onClickHandler: Function
) => (value: any, tableMeta: any) => {
    return (editingRowId === tableMeta.rowIndex) ?
        <Tooltip title={(editingRowId === tableMeta.rowIndex) ? hint : ""}>
            <Typography
                onClick={() => {
                    const [id] = tableMeta.rowData;
                    onClickHandler(id);
                }}
                style={{
                    fontSize: "0.875rem",
                    paddingTop: "13px",
                    cursor: "pointer",
                    paddingBottom: "6px",
                    borderBottom: "1px solid black"
                }}
            >
                {printContent(currentValue)}
            </Typography>
        </Tooltip>
        :
        <Typography
            style={{ fontSize: "0.875rem", paddingTop: "5px" }}
        >
            {printContent(value)}
        </Typography>

}
