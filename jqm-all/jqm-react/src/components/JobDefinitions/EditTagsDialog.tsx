import { Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button, TextField, createStyles, makeStyles, Theme } from "@material-ui/core";
import React, { useState } from "react";
import { JobDefinitionTags } from "./JobDefinition";



const useStyles = makeStyles((theme: Theme) =>
    createStyles({
        TextField: {
            padding: theme.spacing(0, 0, 3),
        },
    })
);

export const EditTagsDialog: React.FC<{
    closeDialog: () => void;
    tags: JobDefinitionTags;
    setTags: (tags: JobDefinitionTags) => void;
}> = ({ closeDialog, tags, setTags }) => {
    const [application, setApplication] = useState<string | undefined>(tags.application);
    const [module, setModule] = useState<string | undefined>(tags.module);
    const [keyword1, setKeyword1] = useState<string | undefined>(tags.keyword1);
    const [keyword2, setKeyword2] = useState<string | undefined>(tags.keyword2);
    const [keyword3, setKeyword3] = useState<string | undefined>(tags.keyword3);

    const classes = useStyles();

    return <Dialog open={true} onClose={closeDialog} aria-labelledby="form-dialog-title" fullWidth maxWidth={"md"}>
        <DialogTitle id="form-dialog-title">Edit tags</DialogTitle>
        <DialogContent>
            <DialogContentText>
                Optionnal tags for classification and queries.
            </DialogContentText>
            <TextField
                className={classes.TextField}
                label="Application"
                value={application}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    setApplication(event.target.value);
                }}
                fullWidth
            />
            <TextField
                className={classes.TextField}
                label="Module"
                value={module}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    setModule(event.target.value);
                }}
                fullWidth
            />
            <TextField
                className={classes.TextField}
                label="Keyword 1"
                value={keyword1}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    setKeyword1(event.target.value);
                }}
                fullWidth
            />
            <TextField
                className={classes.TextField}
                label="Keyword 2"
                value={keyword2}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    setKeyword2(event.target.value);
                }}
                fullWidth
            />
            <TextField
                className={classes.TextField}
                label="Keyword 3"
                value={keyword3}
                onChange={(event: React.ChangeEvent<HTMLInputElement>) => {
                    setKeyword2(event.target.value);
                }}
                fullWidth
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
                onClick={() => {
                    setTags({
                        application: application,
                        module: module,
                        keyword1: keyword1,
                        keyword2: keyword2,
                        keyword3: keyword3
                    });
                    closeDialog();
                }}
                color="primary">
                Save
            </Button>
        </DialogActions>
    </Dialog>
}

