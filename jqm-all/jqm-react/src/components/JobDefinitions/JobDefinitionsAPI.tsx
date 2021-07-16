import { useState, useCallback } from "react";
import APIService from "../../utils/APIService";
import { useNotificationService } from "../../utils/NotificationService";
import { JobDefinition, JobType } from "./JobDefinition";


function pathTypeToApplicationType(pathType: string): JobType {
    switch (pathType) {
        case "DIRECTEXECUTABLE":
            return JobType.process;
        case "POWERSHELLCOMMAND":
        case "DEFAULTSHELLCOMMAND":
            return JobType.shell;
        default:
            return JobType.java;
    }
}

export const useJobDefinitionsAPI = () => {
    const { displayError, displaySuccess } = useNotificationService();

    const [jobDefinitions, setJobDefinitions] = useState<JobDefinition[] | null>();

    const fetchJobDefinitions = useCallback(async () => {
        // return APIService.get("/jd")
        //     .then((mappings) => setJobDefinitions(mappings))
        //     .catch(displayError);
        const response = [{
            id: 1,
            description: "what the job does",
            queueId: 331,
            javaClassName: "com.company.product.ClassName",
            canBeRestarted: true,
            highlander: true,
            jarPath: "echo \"hello\"",
            enabled: true,
            parameters: [{ "key": "test", "value": "1234" }],
            applicationName: "Shell Job",
            pathType: "POWERSHELLCOMMAND",
            schedules: [{ id: 12, cronExpression: "* * * * * ", queue: 329, parameters: [] }],
            application: "application",
            module: "module",
            keyword1: "keyword 1",
            keyword2: "keyword 2",
            keyword3: "keyword 3"
        },
        {
            id: 2,
            description: "what the job does",
            queueId: 2,
            javaClassName: "com.company.product.ClassName",
            canBeRestarted: true,
            highlander: false,
            jarPath: "relativepath/to/file.jar",
            enabled: true,
            parameters: [{ "key": "test", "value": "1234" }],
            applicationName: "Java Job",
            pathType: "FS",
            schedules: [],
            module: "module",
            keyword2: "keyword 2",
            keyword3: "keyword 3"
        },
        {
            id: 3,
            description: "what the job does",
            queueId: 2,
            javaClassName: "com.company.product.ClassName",
            canBeRestarted: true,
            highlander: false,
            jarPath: "path/to/executable",
            enabled: true,
            applicationName: "Process Job",
            pathType: "DIRECTEXECUTABLE",
            schedules: [],
            parameters: []
        }];

        setJobDefinitions(response.map(
            jobDefinition => {
                return {
                    ...jobDefinition,
                    tags: {
                        application: jobDefinition.application,
                        module: jobDefinition.module,
                        keyword1: jobDefinition.keyword1,
                        keyword2: jobDefinition.keyword2,
                        keyword3: jobDefinition.keyword3,
                    },
                    jobType: pathTypeToApplicationType(jobDefinition.pathType),
                    properties: {
                        javaClassName: jobDefinition.javaClassName,
                        jarPath: jobDefinition.jarPath,
                        jobType: pathTypeToApplicationType(jobDefinition.pathType),
                        pathType: jobDefinition.pathType
                    }
                }
            }
        ));
    }, [displayError]);

    const createJobDefinition = useCallback(
        async (newJobDefinition: JobDefinition) => {
            return APIService.post("/jd", newJobDefinition)
                .then(() => {
                    fetchJobDefinitions();
                    displaySuccess(`Successfully created job definition ${newJobDefinition.applicationName}`);
                })
                .catch(displayError);
        },
        [fetchJobDefinitions, displayError, displaySuccess]
    );

    const deleteJobDefinitions = useCallback(
        async (jobDefinitionsIds: number[]) => {
            return await Promise.all(
                jobDefinitionsIds.map((id) => APIService.delete("/jd/" + id))
            )
                .then(() => {
                    fetchJobDefinitions();
                    displaySuccess(
                        `Successfully deleted job definition${jobDefinitionsIds.length > 1 ? "s" : ""}`);
                })
                .catch(displayError);
        },
        [fetchJobDefinitions, displayError, displaySuccess]
    );

    const updateJobDefinition = useCallback(
        async (
            jobDefinition: JobDefinition
        ) => {
            // TODO: convert to API format
            return APIService.put("/jd/" + jobDefinition.id, jobDefinition)
                .then(() => {
                    fetchJobDefinitions();
                    displaySuccess("Successfully saved job definition");
                })
                .catch(displayError);
        },
        [fetchJobDefinitions, displayError, displaySuccess]
    );
    return {
        jobDefinitions,
        fetchJobDefinitions,
        createJobDefinition,
        updateJobDefinition,
        deleteJobDefinitions,
    };
};

export default useJobDefinitionsAPI;
