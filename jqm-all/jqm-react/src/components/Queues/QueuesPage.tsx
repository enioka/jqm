import React, { useEffect, useState, useCallback, useRef } from "react";
import { Container, Grid, IconButton, Tooltip } from "@material-ui/core";
import CircularProgress from "@material-ui/core/CircularProgress";
import MUIDataTable from "mui-datatables";
import HelpIcon from "@material-ui/icons/Help";
import RefreshIcon from "@material-ui/icons/Refresh";
import AddCircleIcon from "@material-ui/icons/AddCircle";
import useQueueCrudApi from "./useQueueCrudApi";
import { CreateQueueModal } from "./CreateQueueModal";
import {
    renderStringCell,
    renderBooleanCell,
    renderActionsCell,
} from "../TableCells";

const QueuesPage: React.FC = () => {
    const [showModal, setShowModal] = useState(false);
    const [editingRowId, setEditingRowId] = useState<number | null>(null);
    const [editingDefaultQueue, setEditingDefaultQueue] = useState<
        boolean | false
    >(false);
    const editingDescriptionInputRef = useRef(null);
    const editingQueueNameInputRef = useRef(null);

    const {
        queues,
        fetchQueues,
        createQueue,
        updateQueue,
        deleteQueues,
    } = useQueueCrudApi();

    useEffect(() => {
        fetchQueues();
    }, []);

    const updateRow = useCallback(
        (id: number) => {
            const { value: name } = editingQueueNameInputRef.current!;
            const { value: description } = editingDescriptionInputRef.current!;
            if (id && name && description) {
                updateQueue({
                    id: id,
                    name,
                    description,
                    defaultQueue: editingDefaultQueue,
                }).then(() => setEditingRowId(null));
            }
        },
        [updateQueue, editingDefaultQueue]
    );

    const handleOnDelete = useCallback(
        (tableMeta) => {
            const [queueId] = tableMeta.rowData;
            deleteQueues([queueId]);
        },
        [deleteQueues]
    );
    const handleOnSave = useCallback(
        (tableMeta) => {
            const [queueId] = tableMeta.rowData;
            updateRow(queueId);
        },
        [updateRow]
    );
    const handleOnCancel = useCallback(() => setEditingRowId(null), []);
    const handleOnEdit = useCallback(
        (tableMeta) => setEditingRowId(tableMeta.rowIndex),
        []
    );

    const columns = [
        {
            name: "id",
            label: "id",
            options: {
                display: "excluded",
            },
        },
        {
            name: "name",
            label: "Name",
            options: {
                filter: true,
                sort: true,
                customBodyRender: renderStringCell(
                    editingQueueNameInputRef,
                    editingRowId
                ),
            },
        },
        {
            name: "description",
            label: "Description",
            options: {
                filter: true,
                sort: true,
                customBodyRender: renderStringCell(
                    editingDescriptionInputRef,
                    editingRowId
                ),
            },
        },
        {
            name: "defaultQueue",
            label: "Is default",
            options: {
                filter: true,
                sort: true,
                customBodyRender: renderBooleanCell(
                    editingRowId,
                    editingDefaultQueue,
                    setEditingDefaultQueue
                ),
            },
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
                        <>
                            <IconButton
                                color="default"
                                aria-label={"add"}
                                onClick={() => setShowModal(true)}
                            >
                                <AddCircleIcon />
                            </IconButton>
                            <CreateQueueModal
                                showModal={showModal}
                                closeModal={() => setShowModal(false)}
                                createQueue={createQueue}
                            />
                        </>
                    </Tooltip>
                    <Tooltip title={"Refresh"}>
                        <IconButton
                            color="default"
                            aria-label={"refresh"}
                            onClick={() => fetchQueues()}
                        >
                            <RefreshIcon />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title={"Help"}>
                        <IconButton color="default" aria-label={"help"}>
                            <HelpIcon />
                        </IconButton>
                    </Tooltip>
                </>
            );
        },
        onRowsDelete: ({ data }: { data: any[] }) => {
            // delete all rows by index
            const queueIds = data.map(({ index }) => {
                const queue = queues ? queues[index] : null;
                return queue ? queue.id : null;
            });
            deleteQueues(queueIds);
        },
    };

    return queues ? (
        <Container>
            <MUIDataTable
                title={"Queues"}
                data={queues}
                columns={columns}
                options={options}
            />
        </Container>
    ) : (
        <Grid container justify="center">
            <CircularProgress />
        </Grid>
    );
};

export default QueuesPage;
