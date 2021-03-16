import React from "react";
import { Switch } from "@material-ui/core";
import DoneIcon from "@material-ui/icons/Done";
import BlockIcon from "@material-ui/icons/Block";

export const renderBooleanCell = (
    editingRowId: number | null,
    booleanValue: boolean,
    setBoolean: Function
) => (value: any, tableMeta: any) => {
    if (editingRowId === tableMeta.rowIndex) {
        return (
            <Switch
                checked={booleanValue}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
                    setBoolean(event.target.checked)
                }
            />
        );
    } else {
        return value ? <DoneIcon /> : <BlockIcon />;
    }
};
