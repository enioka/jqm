import React, { useState } from "react";
import { Button, FormControl, FormGroup, Input, InputLabel, MenuItem, Select, Switch } from "@material-ui/core";
import { makeStyles, Theme, createStyles } from "@material-ui/core/styles";
import Dialog from "@material-ui/core/Dialog";
import DialogTitle from "@material-ui/core/DialogTitle";
import DialogContent from "@material-ui/core/DialogContent";
import DialogActions from "@material-ui/core/DialogActions";
import TextField from "@material-ui/core/TextField/TextField";
import FormControlLabel from "@material-ui/core/FormControlLabel";
import { Queue } from "../Queues/Queue";
import { JobDefinition, JobType } from "./JobDefinition";
import { SpecificPropertiesForm } from "./EditSpecificPropertiesDialog";

const useStyles = makeStyles((theme: Theme) =>
    createStyles({
        TextField: {
            padding: theme.spacing(0, 0, 3),
        },
        FormControlLabel: {
            padding: theme.spacing(0, 0, 0),
            margin: theme.spacing(0, 0, 0, 0),
            alignItems: "start",
        },
    })
);

export const CreateJobDefinitionDialog: React.FC<{
    closeDialog: () => void;
    createJobDefinition: (jobDefinition: JobDefinition) => void;
    queues: Queue[];
}> = ({ closeDialog, createJobDefinition, queues }) => {
    const [queueId, setQueueId] = useState<number | null>(null);
    const [applicationName, setApplicationName] = useState("");
    const [description, setDescription] = useState("")
    const [enabled, setEnabled] = useState(true);
    const [highlander, setHighlander] = useState(false);
    const [jobType, setJobType] = useState<JobType>(JobType.java);
    const [javaClassName, setJavaClassName] = useState<string>("");
    const [jarPath, setJarPath] = useState<string>("");
    const [pathType, setPathType] = useState<string>("");


    const classes = useStyles();
    return (
        <Dialog
            open={true}
            onClose={closeDialog}
            aria-labelledby="form-dialog-title"
            fullWidth maxWidth={"md"}
        >
            <DialogTitle>Create job definition</DialogTitle>
            <DialogContent>

                <TextField
                    className={classes.TextField}
                    label="Name*"
                    value={applicationName}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setApplicationName(event.target.value);
                    }}
                    fullWidth
                />
                <TextField
                    className={classes.TextField}
                    label="Description"
                    value={description}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setDescription(event.target.value);
                    }}
                    fullWidth
                />

                <FormControl fullWidth style={{ marginBottom: "16px" }}>
                    <InputLabel id="queue-id-select-label">Queue*</InputLabel>
                    <Select
                        labelId="queue-id-select-label"
                        fullWidth
                        value={queueId}
                        onChange={(event: React.ChangeEvent<{ value: unknown }>) => {
                            setQueueId(event.target.value as number);
                        }}
                        input={<Input />}
                    >
                        {queues!.map((queue: Queue) => (
                            <MenuItem key={queue.id} value={queue.id}>
                                {queue.name}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>

                <FormGroup>
                    <FormControlLabel
                        className={classes.FormControlLabel}
                        control={
                            <Switch
                                checked={enabled}
                                onChange={(
                                    event: React.ChangeEvent<HTMLInputElement>
                                ) => {
                                    setEnabled(event.target.checked);
                                }}
                            />
                        }
                        label="Enabled"
                        labelPlacement="top"
                    />
                </FormGroup>
                <FormGroup>

                    <FormControlLabel
                        className={classes.FormControlLabel}
                        control={
                            <Switch
                                checked={highlander}
                                onChange={(
                                    event: React.ChangeEvent<HTMLInputElement>
                                ) => {
                                    setHighlander(event.target.checked);
                                }}
                            />
                        }
                        label="Highlander"
                        labelPlacement="top"
                    />
                </FormGroup>

                <FormControl fullWidth style={{ marginBottom: "16px" }}>
                    <InputLabel id="job-type-select-label">Job type*</InputLabel>
                    <Select
                        labelId="job-type-select-label"
                        fullWidth
                        value={jobType} // TODO: fix
                        onChange={(event: React.ChangeEvent<{ value: unknown }>) => {
                            console.log(event.target.value as JobType)
                            setJobType(event.target.value as JobType);
                        }}
                        input={<Input />}
                    >
                        {Object.keys(JobType).map(key => {
                            console.log(key);
                            return <MenuItem key={key} value={key}>
                                {key}
                            </MenuItem>
                        }
                        )}
                    </Select>
                </FormControl>
                <SpecificPropertiesForm
                    jobType={jobType}
                    jarPath={jarPath}
                    setJarPath={setJarPath}
                    javaClassName={javaClassName}
                    setJavaClassName={setJavaClassName}
                    pathType={pathType}
                    setPathType={setPathType}
                />
            </DialogContent>
            <DialogActions>
                <Button
                    variant="contained"
                    size="small"
                    onClick={closeDialog}
                    style={{ margin: "8px" }}
                >
                    Cancel
                </Button>
                <Button
                    variant="contained"
                    size="small"
                    color="primary"
                    disabled={!queueId || !applicationName}
                    style={{ margin: "8px" }}
                    onClick={() => {
                        createJobDefinition({
                            enabled: enabled,
                            queueId: queueId!,
                            description: description,
                            applicationName: applicationName,
                            highlander: highlander,
                            canBeRestarted: true,
                            parameters: [],
                            tags: { application: undefined, module: undefined, keyword1: undefined, keyword2: undefined, keyword3: undefined },
                            schedules: [],
                            properties: {
                                jobType: jobType,
                                pathType: pathType,
                                jarPath: jarPath,
                                javaClassName: javaClassName
                            }
                        });
                        closeDialog();
                    }}
                >
                    Create
                </Button>
            </DialogActions>
        </Dialog>
    );
};
