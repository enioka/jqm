import { Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, TextField, createStyles, makeStyles, Theme, FormControl, FormControlLabel, FormLabel, Radio, RadioGroup, FormHelperText } from "@material-ui/core";
import React, { useState } from "react";
import { JobDefinitionSpecificProperties, JobType } from "./JobDefinition";



const useStyles = makeStyles((theme: Theme) =>
    createStyles({
        TextField: {
            padding: theme.spacing(0, 0, 3),
        },
    })
);

export const SpecificPropertiesForm: React.FC<{
    jobType: JobType;
    jarPath: string;
    setJarPath: (jarPath: string) => void;
    javaClassName: string;
    setJavaClassName: (javaClassName: string) => void;
    pathType: string;
    setPathType: (pathType: string) => void;
}> = ({ jobType, jarPath, setJarPath, javaClassName, setJavaClassName, pathType, setPathType }) => {
    const classes = useStyles();

    return <>{
        jobType === JobType.java && (
            <>
                <TextField
                    className={classes.TextField}
                    label="Path to the jar file"
                    value={jarPath}
                    helperText={"The relative path to the jar containing the class to run. It is relative to the 'directory containing jars' parameter of the different nodes."}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setJarPath(event.target.value);
                    }}
                    fullWidth
                />
                < TextField
                    className={classes.TextField}
                    label="Class to launch"
                    value={javaClassName}
                    helperText={"The fully qualified name of the class to run. (it must either have a main function, or implement Runnable, or inherit from JobBase)."}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setJavaClassName(event.target.value);
                    }}
                    fullWidth
                />
            </>)
    }
        {
            jobType === JobType.process && (
                <TextField
                    className={classes.TextField}
                    label="Path to executable"
                    value={jarPath}
                    helperText={"Current path is node job directory. Do not add parameters here, just a path. Process is launched with the value of the provided parameters, sorted by key. Key is only used for sorting."}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setJarPath(event.target.value);
                    }}
                    fullWidth
                />
            )
        }
        {
            jobType === JobType.shell && (
                <>
                    <FormControl component="fieldset" style={{ marginBottom: "16px" }}>
                        <FormLabel component="legend">Shell</FormLabel>
                        <RadioGroup aria-label="Shell" name="shell" value={pathType} onChange={
                            (event: React.ChangeEvent<HTMLInputElement>) => {
                                setPathType(event.target.value);
                            }
                        }>
                            <FormControlLabel value="DEFAULTSHELLCOMMAND" control={<Radio />} label="Default OS shell" />
                            <FormControlLabel value="POWERSHELLCOMMAND" control={<Radio />} label="Powershell" />
                        </RadioGroup>
                        <FormHelperText>Default shell is /bin/sh or cmd.exe. Powershell is Powershell core under Linux and full under Windows.</FormHelperText>
                    </FormControl>
                    <TextField
                        className={classes.TextField}
                        label="Shell command"
                        value={jarPath}
                        helperText={"Current path is node job directory. JQM environment variables (and others) may be used. 1000 max (988 remaining)."}
                        onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                            setJarPath(event.target.value);
                        }}
                        fullWidth
                    />
                </>
            )
        }</>
}

export const EditSpecificPropertiesDialog: React.FC<{
    closeDialog: () => void;
    properties: JobDefinitionSpecificProperties;
    setProperties: (properties: JobDefinitionSpecificProperties) => void;
}> = ({ closeDialog, properties, setProperties }) => {
    const [javaClassName, setJavaClassName] = useState<string>(properties.javaClassName);
    const [jarPath, setJarPath] = useState<string>(properties.jarPath);
    const [pathType, setPathType] = useState<string>(properties.pathType)

    return <Dialog open={true} onClose={closeDialog} aria-labelledby="form-dialog-title" fullWidth maxWidth={"md"}>
        <DialogTitle id="form-dialog-title">Edit {properties.jobType} specific properties</DialogTitle>
        <DialogContent>
            <SpecificPropertiesForm
                jobType={properties.jobType!!}
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
                style={{ margin: "8px" }}
                onClick={closeDialog}>
                Cancel
            </Button>
            <Button variant="contained"
                size="small"
                style={{ margin: "8px" }}
                disabled={!jarPath || !javaClassName}
                onClick={() => {
                    setProperties({
                        ...properties,
                        jarPath: jarPath!!,
                        javaClassName: javaClassName!!,
                        pathType: pathType
                    });
                    closeDialog();
                }}
                color="primary">
                Save
            </Button>
        </DialogActions>
    </Dialog>
}

