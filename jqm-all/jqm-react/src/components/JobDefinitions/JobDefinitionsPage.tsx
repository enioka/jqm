import React, { useEffect, useState, useCallback, useRef } from "react";
import { Container, Grid, IconButton, MenuItem, Tooltip } from "@material-ui/core";
import CircularProgress from "@material-ui/core/CircularProgress";
import MUIDataTable from "mui-datatables";
import HelpIcon from "@material-ui/icons/Help";
import RefreshIcon from "@material-ui/icons/Refresh";
import {
    renderInputCell,
    renderBooleanCell,
    renderActionsCell,
} from "../TableCells";

import { useQueueAPI } from "../Queues/QueueAPI";
import useJobDefinitionsAPI from "./JobDefinitionsAPI";
import { renderArrayCell } from "../TableCells/renderArrayCell";
import MappingsPage from "../Mappings/MappingsPage";
import { Queue } from "../Queues/Queue";
import { Typography } from "@material-ui/core";
import { JobDefinitionParameter, JobDefinitionSchedule, JobDefinitionSpecificProperties, JobDefinitionTags, JobType } from "./JobDefinition";
import AddCircleIcon from "@material-ui/icons/AddCircle";
import { renderDialogCell } from "../TableCells/renderDialogCell";
import { EditTagsDialog } from "./EditTagsDialog";
import { EditSpecificPropertiesDialog } from "./EditSpecificPropertiesDialog";
import { EditParametersDialog } from "./EditParametersDialog";
import { CreateJobDefinitionDialog } from "./CreateJobDefinitionDialog";

export const JobDefinitionsPage: React.FC = () => {
    const [editingRowId, setEditingRowId] = useState<number | null>(null);
    const applicationNameInputRef = useRef(null);
    const descriptionInputRef = useRef(null);
    const [queueId, setQueueId] = useState<number | null>(null);
    const [enabled, setEnabled] = useState<boolean>(true);
    const [highlander, setHighlander] = useState<boolean>(false);
    const [properties, setProperties] = useState<JobDefinitionSpecificProperties | null>(null);
    const [tags, setTags] = useState<JobDefinitionTags | null>(null);
    const [parameters, setParameters] = useState<Array<JobDefinitionParameter> | []>([]);
    const [schedules, setSchedules] = useState<Array<JobDefinitionSchedule>>([])

    const [showCreateDialog, setShowCreateDialog] = useState<boolean>(false);
    const [editPropertiesJobDefinitionId, setEditPropertiesJobDefinitionId] = useState<string | null>(null);
    const [editTagsJobDefinitionId, setEditTagsJobDefinitionId] = useState<string | null>(null);
    const [editParametersJobDefinitionId, setEditParametersJobDefinitionId] = useState<string | null>(null);
    const [editSchedulesJobDefinitionId, setEditSchedulesJobDefinitionId] = useState<string | null>(null);

    const {
        jobDefinitions,
        fetchJobDefinitions,
        createJobDefinition,
        updateJobDefinition,
        deleteJobDefinitions,
    } = useJobDefinitionsAPI();

    const {
        queues,
        fetchQueues,
    } = useQueueAPI();

    const refresh = () => {
        fetchQueues();
        fetchJobDefinitions();
    }

    useEffect(() => {
        refresh()
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);


    const handleOnDelete = useCallback(
        (tableMeta) => {
            const [jobDefinitionId] = tableMeta.rowData;
            deleteJobDefinitions([jobDefinitionId]);
        },
        [deleteJobDefinitions]
    );

    const handleOnSave = useCallback(
        (tableMeta) => {
            const [jobDefinitionId] = tableMeta.rowData;
            const { value: applicationName } = applicationNameInputRef.current!;
            const { value: description } = descriptionInputRef.current!;

            if (jobDefinitionId && applicationName && queueId) {
                updateJobDefinition({
                    id: jobDefinitionId,
                    applicationName: applicationName,
                    description: description,
                    enabled: enabled,
                    queueId: queueId,
                    highlander: highlander,
                    canBeRestarted: true,
                    schedules: schedules!!,
                    parameters: parameters!!,
                    properties: properties!!,
                    tags: tags!!
                }).then(() => setEditingRowId(null));
            }
        },
        [updateJobDefinition, enabled, queueId, highlander, parameters, properties, tags, schedules]
    );

    const handleOnCancel = useCallback(() => setEditingRowId(null), []);

    const handleOnEdit = useCallback(
        (tableMeta) => {
            console.log(tableMeta)
            setQueueId(tableMeta.rowData[3]);
            setEnabled(tableMeta.rowData[4]);
            setHighlander(tableMeta.rowData[5])
            setProperties(tableMeta.rowData[7]);
            setTags(tableMeta.rowData[8]);
            setParameters(tableMeta.rowData[9]);
            setSchedules(tableMeta.rowData[10]);
            setEditingRowId(tableMeta.rowIndex);
        },
        []
    );

    const printJobSpecificProperties = (value: JobDefinitionSpecificProperties | null) => {
        if (!value) {
            return "";
        }

        if (value.jobType === JobType.java) {
            return (
                <>
                    Path to the jar file: {value.jarPath}<br />
                    Class to launch: {value.javaClassName}
                </>
            );
        }
        else if (value.jobType === JobType.shell) {
            return (
                <>
                    Shell: {value.pathType === "POWERSHELLCOMMAND" ? "Powershell" : "Default OS shell"}<br />
                    Shell command: {value.jarPath}
                </>
            )
        } else {
            return (
                <>
                    Path to executable: {value.jarPath}
                </>
            )
        }
    }

    const printJobTags = (value: JobDefinitionTags | null) => {
        if (!value) {
            return [];
        }
        const result = []
        if (value.application) {
            result.push(<>Application: {value.application} <br /></>);
        }
        if (value.module) {
            result.push(<>Module: {value.module} <br /></>);
        }
        if (value.keyword1) {
            result.push(<>Keyword 1: {value.keyword1} <br /></>);
        }
        if (value.keyword2) {
            result.push(<>Keyword 2: {value.keyword2} <br /></>);
        }
        if (value.keyword3) {
            result.push(<>Keyword 3: {value.keyword3}</>);
        }
        return result;
    }

    const printJobParameters = (value: Array<JobDefinitionParameter>) => {
        return value.map((parameter =>
            `${parameter.key}: ${parameter.value}`
        )).join(", ");
    }

    const printJobSchedules = (value: Array<JobDefinitionSchedule>) => {
        return value.map((parameter =>
            `${parameter.cronExpression}`
        )).join(", ");
    }

    const columns = [
        {
            name: "id",
            label: "id",
            options: {
                display: "excluded",
            },
        },
        {
            name: "applicationName",
            label: "Name*",
            options: {
                hint: "The key used to designate the Job Definition in the different APIs and dialogs",
                filter: true,
                sort: true,
                customBodyRender: renderInputCell(
                    applicationNameInputRef,
                    editingRowId,
                    true,
                ),
            },
        },
        {
            name: "description",
            label: "Description",
            options: {
                hint: "A human-readable description of what the job does",
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
            name: "queueId",
            label: "Default queue*",
            options: {
                hint: "The queue which will be used when submitting execution requests (if no specific queue is given at request time)",
                filter: false,
                sort: false,
                customBodyRender: renderArrayCell(
                    editingRowId,
                    queues ? queues!.map((queue: Queue) => (
                        <MenuItem key={queue.id} value={queue.id}>
                            {queue.name}
                        </MenuItem>
                    )) : [],
                    (element: number) => queues?.find(x => x.id === element)?.name || "",
                    queueId,
                    setQueueId,
                    false
                )

            },
        },
        {
            name: "enabled",
            label: "Enabled",
            options: {
                hint: "If disabled, all instances will always succeed instantly",
                filter: true,
                sort: true,
                customBodyRender: renderBooleanCell(
                    editingRowId,
                    enabled,
                    setEnabled
                ),
            },
        },
        {
            name: "highlander",
            label: "Highlander",
            options: {
                hint: "If checked, there can never be more than one instance of the Job Definition running at the same time, as well as no more than one waiting in any queue",
                filter: true,
                sort: true,
                customBodyRender: renderBooleanCell(
                    editingRowId,
                    highlander,
                    setHighlander
                ),
            },
        },
        {
            name: "jobType",
            label: "Job type",
            options: {

                filter: true,
                sort: true,
                customBodyRender: (value: any, tableMeta: any) =>
                    <Typography
                        style={{ fontSize: "0.875rem", paddingTop: "5px" }}
                    >
                        {value}
                    </Typography>
            }
        },
        {
            name: "properties",
            label: "Properties*",
            options: {
                hint: "Specific properties depending on the job type",
                filter: false,
                sort: false,
                customBodyRender:
                    renderDialogCell(
                        editingRowId,
                        "Click to edit specific properties",
                        properties,
                        printJobSpecificProperties,
                        setEditPropertiesJobDefinitionId
                    )
            }
        },
        {
            name: "tags",
            label: "Tags",
            options: {
                hint: "Optionnal tags for classification and queries",
                filter: false,
                sort: false,
                customBodyRender:
                    renderDialogCell(
                        editingRowId,
                        "Click to edit tags",
                        tags,
                        printJobTags,
                        setEditTagsJobDefinitionId
                    )
            }
        },
        {
            name: "parameters",
            label: "Parameters",
            options: {
                filter: false,
                sort: false,
                customBodyRender:
                    renderDialogCell(
                        editingRowId,
                        "Click to edit parameters",
                        parameters,
                        printJobParameters,
                        setEditParametersJobDefinitionId
                    )
            }
        },
        {
            name: "schedules",
            label: "Schedules",
            options: {
                filter: false,
                sort: false,
                customBodyRender:
                    renderDialogCell(
                        editingRowId,
                        "Click to edit schedules",
                        schedules,
                        printJobSchedules,
                        setEditSchedulesJobDefinitionId
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
                            onClick={() => refresh()}
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
            const jobDefinitionIds: number[] = [];
            data.forEach(({ index }) => {
                const jobDefinition = jobDefinitions ? jobDefinitions[index] : null;
                if (jobDefinition) {
                    jobDefinitionIds.push(jobDefinition.id!);
                }
            });
            deleteJobDefinitions(jobDefinitionIds);
        },
    };

    return jobDefinitions && queues ? (
        <Container maxWidth={false}>
            <MUIDataTable
                title={"Job definitions"}
                data={jobDefinitions}
                columns={columns}
                options={options}
            />
            {showCreateDialog && (
                <CreateJobDefinitionDialog
                    closeDialog={() => setShowCreateDialog(false)}
                    queues={queues}
                    createJobDefinition={createJobDefinition}

                />
            )}
            {editTagsJobDefinitionId !== null &&
                <EditTagsDialog
                    closeDialog={() => setEditTagsJobDefinitionId(null)}
                    tags={tags!!}
                    setTags={(tags: JobDefinitionTags) => setTags(tags)}
                />
            }
            {editPropertiesJobDefinitionId != null &&
                <EditSpecificPropertiesDialog
                    closeDialog={() => setEditPropertiesJobDefinitionId(null)}
                    properties={properties!!}
                    setProperties={(properties: JobDefinitionSpecificProperties) => setProperties(properties)}
                />
            }
            {editParametersJobDefinitionId != null &&
                <EditParametersDialog
                    closeDialog={() => setEditParametersJobDefinitionId(null)}
                    parameters={parameters!!}
                    setParameters={(parameters: Array<JobDefinitionParameter>) => setParameters(parameters)}
                />
            }
            {editSchedulesJobDefinitionId != null &&
                <></>}
        </Container>
    ) : (
        <Grid container justify="center">
            <CircularProgress />
        </Grid>
    );
};

export default MappingsPage;
