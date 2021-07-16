import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, createStyles, makeStyles, Theme, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from "@material-ui/core";
import React, { useState } from "react";
import { JobDefinitionParameter } from "./JobDefinition";
import DeleteIcon from "@material-ui/icons/Delete";



const useStyles = makeStyles((theme: Theme) =>
    createStyles({
        TextField: {
            padding: theme.spacing(0, 0, 3),
        },
    })
);

export const EditParametersDialog: React.FC<{
    closeDialog: () => void;
    parameters: Array<JobDefinitionParameter>;
    setParameters: (parameters: Array<JobDefinitionParameter>) => void;
}> = ({ closeDialog, parameters, setParameters }) => {
    const [editedParameters, setEditedParameters] = useState<Array<JobDefinitionParameter>>(parameters);

    const [key, setKey] = useState<string>("");
    const [value, setValue] = useState<string>("");

    const classes = useStyles();

    return <Dialog open={true} onClose={closeDialog} aria-labelledby="form-dialog-title" fullWidth maxWidth={"md"}>
        <DialogTitle id="form-dialog-title">Edit tags</DialogTitle>
        <DialogContent>
            <>
                <TextField
                    className={classes.TextField}
                    label="Key"
                    value={key}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setKey(event.target.value);
                    }}
                    fullWidth
                />
                <TextField
                    className={classes.TextField}
                    label="Value"
                    value={value}
                    onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                        setValue(event.target.value);
                    }}
                    fullWidth
                />

                <Button variant="contained"
                    size="small"
                    style={{ marginTop: "16px" }}
                    disabled={editedParameters.filter((parameter) => parameter.key === key).length > 0}
                    onClick={() => {
                        setEditedParameters([...editedParameters,
                        {
                            key: key,
                            value: value
                        }])
                    }}
                    color="primary">
                    Add parameter
                </Button>
                <TableContainer component={Paper}>
                    <Table size="small" aria-label="a dense table">
                        <TableHead>
                            <TableRow>
                                <TableCell>Code</TableCell>
                                <TableCell>Action</TableCell>
                                <TableCell>Object type</TableCell>
                                <TableCell align="right"></TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {editedParameters.map((parameter, index) => (
                                <TableRow key={parameter.key}>
                                    <TableCell component="th" scope="row">
                                        {parameter.key}
                                    </TableCell>
                                    <TableCell >{parameter.value}</TableCell>
                                    <TableCell >  <IconButton
                                        color="default"
                                        aria-label={"delete"}
                                        onClick={() => {
                                            setEditedParameters(editedParameters.filter((_, i) => i !== index))
                                        }}
                                    >
                                        <DeleteIcon />
                                    </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}

                        </TableBody>
                    </Table>
                </TableContainer>
            </>

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
                onClick={() => {
                    setParameters(editedParameters);
                    closeDialog();
                }}
                color="primary">
                Save
            </Button>
        </DialogActions>
    </Dialog>
}

