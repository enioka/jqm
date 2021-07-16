
import { Container, Grid, CircularProgress, IconButton, Tooltip } from "@material-ui/core";
import MUIDataTable, { MUIDataTableColumnDef } from "mui-datatables";
import React, { useCallback, useEffect, useRef, useState } from "react";
import HelpIcon from "@material-ui/icons/Help";
import RefreshIcon from "@material-ui/icons/Refresh";
import AddCircleIcon from "@material-ui/icons/AddCircle";
import { useRoleAPI } from "./RoleAPI";
import { renderActionsCell, renderInputCell } from "../TableCells";
import { EditPermissionsDialog } from "./EditPermissionsDialog";
import { CreateRoleDialog } from "./CreateRoleDialog";
import { renderDialogCell } from "../TableCells/renderDialogCell";


const RolesPage: React.FC = () => {

    const [editingRowId, setEditingRowId] = useState<number | null>(null);
    const nameInputRef = useRef(null);
    const descriptionInputRef = useRef(null);
    const { roles, fetchRoles, createRole, updateRole, deleteRoles } = useRoleAPI();
    const [showCreateDialog, setShowCreateDialog] = useState(false);
    const [permissions, setPermissions] = useState<string[] | null>(null);
    const [editPermissionsRoleId, setEditPermissionsRoleId] = useState<string | null>(null);

    useEffect(() => {
        fetchRoles();
    }, [fetchRoles]);

    const updateRow = useCallback(
        (id: number) => {
            if (!editingRowId) {
                return;
            }
            const { value: name } = nameInputRef.current!;
            const { value: description } = descriptionInputRef.current!;
            if (id && name && permissions != null) {
                updateRole({
                    id: id,
                    name: name,
                    description: description,
                    permissions: permissions!!
                }).then(() => setEditingRowId(null));
            }
        },
        [updateRole, editingRowId, permissions]
    );

    const handleOnDelete = useCallback(
        (tableMeta) => {
            const [roleId] = tableMeta.rowData;
            deleteRoles([roleId]);
        },
        [deleteRoles]
    );

    const handleOnSave = useCallback(
        (tableMeta) => {
            const [roleId] = tableMeta.rowData;
            updateRow(roleId);
        },
        [updateRow]
    );

    const handleOnCancel = useCallback(() => setEditingRowId(null), []);

    const handleOnEdit = useCallback(
        (tableMeta) => {
            setEditingRowId(tableMeta.rowIndex)
            setPermissions(tableMeta.rowData[3]);
        },
        []
    );

    const columns: MUIDataTableColumnDef[] = [
        {
            name: "id",
            label: "id",
            options: {
                display: "excluded",
            },
        },
        {
            name: "name",
            label: "Name*",
            options: {
                hint: "Name of the role. Must be unique.",
                filter: true,
                sort: true,
                customBodyRender: renderInputCell(
                    nameInputRef,
                    editingRowId,
                    true
                ),
            },
        },
        {
            name: "description",
            label: "Description",
            options: {
                hint: "What the role does.",
                filter: true,
                sort: true,
                customBodyRender: renderInputCell(
                    descriptionInputRef,
                    editingRowId,
                    true,
                ),
            },
        },
        {
            name: "permissions",
            label: "Permissions",
            options: {
                filter: false,
                sort: false,
                customBodyRender:
                    renderDialogCell(
                        editingRowId,
                        "Click to edit permissions",
                        permissions,
                        (value: any[]) => (value as string[]).join(", "),
                        setEditPermissionsRoleId
                    )
            }
        },
        {
            name: "",
            label: "Actions",
            options: {
                filter: true,
                sort: true,
                customBodyRender: renderActionsCell(
                    handleOnCancel,
                    handleOnSave,
                    handleOnDelete,
                    editingRowId,
                    handleOnEdit
                ),
            },
        },
    ];


    const options = {
        setCellProps: () => ({ fullWidth: "MuiInput-fullWidth" }),
        download: false,
        print: false,
        customToolbar: () => {
            return (
                <>
                    <Tooltip title={"Add line"}>
                        <IconButton
                            color="default"
                            aria-label={"add"}
                            onClick={() => setShowCreateDialog(true)}
                        >
                            <AddCircleIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title={"Refresh"}>
                        <IconButton
                            color="default"
                            aria-label={"refresh"}
                            onClick={() => fetchRoles()}
                        >
                            <RefreshIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title={"Help"}>
                        <IconButton
                            color="default"
                            aria-label={"help"}
                            onClick={() => {
                                //
                            }}
                        >
                            <HelpIcon />
                        </IconButton>
                    </Tooltip>
                </>
            );
        },

        onRowsDelete: ({ data }: { data: any[] }) => {
            // delete all rows by index
            const roleIds: number[] = [];
            data.forEach(({ index }) => {
                const role = roles ? roles[index] : null;
                if (role) {
                    roleIds.push(role.id!);
                }
            });
            deleteRoles(roleIds);
        },
    };

    if (roles) {
        return (
            <Container maxWidth={false}>
                <MUIDataTable
                    title={"Roles"}
                    data={roles}
                    columns={columns}
                    options={options}
                />
                {editPermissionsRoleId !== null &&
                    <EditPermissionsDialog
                        closeDialog={() => setEditPermissionsRoleId(null)}
                        permissions={permissions!!}
                        setPermissions={(permissions: string[]) => setPermissions(permissions)}
                    />
                }
                {showCreateDialog && (
                    <CreateRoleDialog
                        closeDialog={() => setShowCreateDialog(false)}
                        createRole={createRole}
                    />
                )}
            </Container>

        );
    } else {
        return (
            <Grid container justify="center">
                <CircularProgress />
            </Grid>
        );
    }
};

export default RolesPage;
