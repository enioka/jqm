export interface JobDefinitionParameter {
    key: string;
    value: string;
}

export interface JobDefinitionSchedule {
    id?: number;
    cronExpression: string; // * * * * *
    queue?: number;
    parameters: Array<JobDefinitionParameter>
}

export interface JobDefinitionTags {
    application?: string;
    module?: string;
    keyword1?: string;
    keyword2?: string;
    keyword3?: string;
}

export interface JobDefinitionSpecificProperties {
    javaClassName: string;
    jarPath: string;
    jobType?: JobType;
    pathType: string;
    // TODO: Specific class loader
}

export enum JobType {
    java = "Java",
    shell = "Shell",
    process = "Process"
};

export interface JobDefinition {
    // Identity
    id?: number;
    applicationName: string; // job definition 1"
    description?: string; // what the job does
    queueId: number;
    enabled: boolean;
    highlander: boolean;
    canBeRestarted: boolean;
    schedules: Array<JobDefinitionSchedule>;
    // Tags
    tags: JobDefinitionTags;
    jobType?: JobType;
    properties: JobDefinitionSpecificProperties,
    // Parameters
    parameters: Array<JobDefinitionParameter>
}
