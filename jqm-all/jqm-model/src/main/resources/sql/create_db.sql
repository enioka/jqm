/* TODO: naming convention of objects ! */
/* TODO: allow prefix and suffix of tables */
/* TODO: correct some field names */
/* TODO: check index on all FK */
/* TODO: check JI indexes (too many) */
/* TODO: rename FK fields to _ID */
/* TODO: separate Node table in TP and ref tables */
/* TODO: long vs int */
/* TODO: make Hitsory columns consistent among themselves */
/* TODO: search for remaining "JPA" mentions */
/* TODO: prepared statements everywhere */
/* TODO: version table and checks */
/* TODO: schema migration */
/* TODO: hunt useless model calsqses - most often a direct  query will be clearer */
/* Use LONG for some tables' ID */
/* Hunt applicationName */
/* TODO: date on text messages */

/* Deployment infra */
CREATE MEMORY TABLE NODE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	DLREPO VARCHAR(1024) NOT NULL,
	DNS VARCHAR(255) NOT NULL,
	ENABLED BOOLEAN,
	EXPORTREPO VARCHAR(1024),
	JMXREGISTRYPORT INTEGER,
	JMXSERVERPORT INTEGER,
	LASTSEENALIVE TIMESTAMP,
	LOADAPIADMIN BOOLEAN,
	LOADAPICLIENT BOOLEAN,
	LOAPAPISIMPLE BOOLEAN,
	NODENAME VARCHAR(100) UNIQUE NOT NULL,
	PORT INTEGER NOT NULL,
	REPO VARCHAR(1024) NOT NULL,
	ROOTLOGLEVEL VARCHAR(10),
	STOP BOOLEAN NOT NULL,
	TMPDIRECTORY VARCHAR(1024)
);

CREATE MEMORY TABLE QUEUE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	DEFAULTQUEUE BOOLEAN,
	DESCRIPTION VARCHAR(1000) NOT NULL,
	NAME VARCHAR(50) UNIQUE NOT NULL,
	TIMETOLIVE INTEGER NULL
);

CREATE MEMORY TABLE DEPLOYMENTPARAMETER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	CLASSID INTEGER,
	ENABLED BOOLEAN,
	LASTMODIFIED TIMESTAMP,
	NBTHREAD INTEGER NOT NULL,
	POLLINGINTERVAL INTEGER NOT NULL,
	NODE INTEGER NOT NULL,
	QUEUE INTEGER NOT NULL,
	CONSTRAINT UK_DEPLOYMENTPARAMETER_QN UNIQUE(QUEUE,NODE),
	CONSTRAINT FK_DEPLOYMENTPARAMETER_1 FOREIGN KEY(NODE) REFERENCES NODE(ID),
	CONSTRAINT FK_DEPLOYMENTPARAMETER_2 FOREIGN KEY(QUEUE) REFERENCES QUEUE(ID)
);

/* Job definition */
CREATE MEMORY TABLE JOBDEF
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	APPLICATION VARCHAR(50),
	APPLICATIONNAME VARCHAR(100) UNIQUE NOT NULL,
	CANBERESTARTED BOOLEAN,
	CHILDFIRSTCLASSLOADER BOOLEAN NOT NULL,
	CLASSLOADERTRACING BOOLEAN NOT NULL,
	DESCRIPTION VARCHAR(1024),
	ENABLED BOOLEAN NOT NULL,
	EXTERNAL BOOLEAN NOT NULL,
	HIDDENJAVACLASSES VARCHAR(1024),
	HIGHLANDER BOOLEAN NOT NULL,
	JARPATH VARCHAR(1024),
	JAVACLASSNAME VARCHAR(100) NOT NULL,
	JAVA_OPTS VARCHAR(200),
	KEYWORD1 VARCHAR(50),
	KEYWORD2 VARCHAR(50),
	KEYWORD3 VARCHAR(50),
	MAXTIMERUNNING INTEGER,
	MODULE VARCHAR(50),
	PATHTYPE VARCHAR(255),
	SPECIFICISOLATIONCONTEXT VARCHAR(20),
	QUEUE_ID INTEGER NOT NULL,
	CONSTRAINT FK_JOBDEF_1 FOREIGN KEY(QUEUE_ID) REFERENCES QUEUE(ID)
);

CREATE MEMORY TABLE JOBDEFPARAMETER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	KEYNAME VARCHAR(50) NOT NULL,
	VALUE VARCHAR(1000) NOT NULL,
	JOBDEF_ID INTEGER,
	CONSTRAINT FK_JOBDEFPARAMETER_1 FOREIGN KEY(JOBDEF_ID) REFERENCES JOBDEF(ID)
);
CREATE INDEX IDX_JOBDEFPARAMETER_FK_1 ON JOBDEFPARAMETER(JOBDEF_ID);

/* Execution and history */
CREATE MEMORY TABLE JOBINSTANCE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	ATTRIBUTIONDATE TIMESTAMP,
	CREATIONDATE TIMESTAMP,
	SENDEMAIL VARCHAR(255),
	EXECUTIONDATE TIMESTAMP,
	APPLICATION VARCHAR(50),
	KEYWORD1 VARCHAR(50),
	KEYWORD2 VARCHAR(50),
	KEYWORD3 VARCHAR(50),
	MODULE VARCHAR(50),
	INTERNALPOSITION DOUBLE NOT NULL,
	PARENTID INTEGER,
	PROGRESS INTEGER,
	SESSIONID VARCHAR(255),
	STATE VARCHAR(50),
	USERNAME VARCHAR(50),
	JD_ID INTEGER,
	NODE_ID INTEGER,
	QUEUE_ID INTEGER,
	HIGHLANDER BOOLEAN,
	CONSTRAINT FK_JOBINSTANCE_1 FOREIGN KEY(JD_ID) REFERENCES JOBDEF(ID),
	CONSTRAINT FK_JOBINSTANCE_2 FOREIGN KEY(NODE_ID) REFERENCES NODE(ID),
	CONSTRAINT FK_JOBINSTANCE_3 FOREIGN KEY(QUEUE_ID) REFERENCES QUEUE(ID)
);
CREATE INDEX IDX_JOBINSTANCE_1 ON JOBINSTANCE(QUEUE_ID,STATE);
CREATE INDEX IDX_JOBINSTANCE_2 ON JOBINSTANCE(JD_ID,STATE);
CREATE INDEX IDX_JOBINSTANCE_3 ON JOBINSTANCE(JD_ID);
CREATE INDEX IDX_JOBINSTANCE_4 ON JOBINSTANCE(NODE_ID);
CREATE INDEX IDX_JOBINSTANCE_5 ON JOBINSTANCE(QUEUE_ID);

CREATE MEMORY TABLE HISTORY
(
	ID INTEGER NOT NULL PRIMARY KEY, /* Not IDENTITY !*/
	APPLICATION VARCHAR(50),
	APPLICATIONNAME VARCHAR(100) NOT NULL,
	ATTRIBUTIONDATE TIMESTAMP,
	EMAIL VARCHAR(255),
	END_DATE TIMESTAMP,
	ENQUEUE_DATE TIMESTAMP,
	EXECUTION_DATE TIMESTAMP,
	HIGHLANDER BOOLEAN,
	INSTANCE_APPLICATION VARCHAR(50),
	INSTANCE_KEYWORD1 VARCHAR(50),
	INSTANCE_KEYWORD2 VARCHAR(50),
	INSTANCE_KEYWORD3 VARCHAR(50),
	INSTANCE_MODULE VARCHAR(50),
	KEYWORD1 VARCHAR(50),
	KEYWORD2 VARCHAR(50),
	KEYWORD3 VARCHAR(50),
	MODULE VARCHAR(50),
	NODENAME VARCHAR(100),
	PARENT_JOB_ID INTEGER,
	PROGRESS INTEGER,
	QUEUE_NAME VARCHAR(50) NOT NULL,
	RETURN_CODE INTEGER,
	SESSION_ID VARCHAR(255),
	STATUS VARCHAR(20),
	USERNAME VARCHAR(255),
	JOBDEF_ID INTEGER,
	NODE_ID INTEGER,
	QUEUE_ID INTEGER,
	CONSTRAINT FK_HISTORY_1 FOREIGN KEY(JOBDEF_ID) REFERENCES JOBDEF(ID),
	CONSTRAINT FK_HISTORY_2 FOREIGN KEY(NODE_ID) REFERENCES NODE(ID),
	CONSTRAINT FK_HISTORY_3 FOREIGN KEY(QUEUE_ID) REFERENCES QUEUE(ID)
);

CREATE MEMORY TABLE DELIVERABLE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	FILE_FAMILY VARCHAR(100),
	FILEPATH VARCHAR(1024),
	JOBID INTEGER NOT NULL,
	ORIGINALFILENAME VARCHAR(1024),
	RANDOMID VARCHAR(200) UNIQUE
);
CREATE INDEX FK_JOBINSTANCE ON DELIVERABLE(JOBID);

CREATE MEMORY TABLE RUNTIMEPARAMETER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	JI_ID INTEGER,
	KEYNAME VARCHAR(50) NOT NULL,
	VALUE VARCHAR(1000) NOT NULL
);
CREATE INDEX IDX_FK_JP_JI ON RUNTIMEPARAMETER(JI_ID);

CREATE MEMORY TABLE MESSAGE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	JI INTEGER NOT NULL,
	TEXT_MESSAGE VARCHAR(1000) NOT NULL
);
CREATE INDEX FK_R_IXD ON MESSAGE(JI);

/* JNDI registry */
CREATE MEMORY TABLE JNDIOBJECTRESOURCE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	AUTH VARCHAR(20),
	DESCRIPTION VARCHAR(250),
	FACTORY VARCHAR(100) NOT NULL,
	LASTMODIFIED TIMESTAMP,
	NAME VARCHAR(100) UNIQUE NOT NULL,
	SINGLETON BOOLEAN,
	TEMPLATE VARCHAR(50),
	TYPE VARCHAR(100) NOT NULL
);

CREATE MEMORY TABLE JNDIOBJECTRESOURCEPARAMETER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	KEYNAME VARCHAR(50) NOT NULL,
	LASTMODIFIED TIMESTAMP,
	VALUE VARCHAR(250) NOT NULL,
	RESOURCE_ID INTEGER,
	CONSTRAINT FK_JNDIOBJECTRESOURCEPARAMETER_1 FOREIGN KEY(RESOURCE_ID) REFERENCES JNDIOBJECTRESOURCE(ID)
);
CREATE INDEX IDX_JNDIOBJECTRESOURCEPARAMETER_FK1 ON JNDIOBJECTRESOURCEPARAMETER(RESOURCE_ID);


/* Security */
CREATE MEMORY TABLE PKI
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	PEMCERT VARCHAR(4000) NOT NULL,
	PEMPK VARCHAR(4000) NOT NULL,
	PRETTYNAME VARCHAR(100) UNIQUE NOT NULL
);

CREATE MEMORY TABLE RROLE
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	DESCRIPTION VARCHAR(254) NOT NULL,
	NAME VARCHAR(100) UNIQUE NOT NULL
);

CREATE MEMORY TABLE RPERMISSION
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	NAME VARCHAR(254) NOT NULL,
	ROLE_ID INTEGER NOT NULL,
	CONSTRAINT FK_RPERMISSION_1 FOREIGN KEY(ROLE_ID) REFERENCES RROLE(ID)
);

CREATE MEMORY TABLE RUSER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	CREATION_DATE TIMESTAMP,
	EMAIL VARCHAR(254),
	EXPIRATION_DATE TIMESTAMP,
	FREETEXT VARCHAR(254),
	HASHSALT VARCHAR(254),
	INTERNAL BOOLEAN,
	LAST_MODIFIED TIMESTAMP,
	LOCKED BOOLEAN,
	LOGIN VARCHAR(100) UNIQUE,
	PASSWORD VARCHAR(254)
);

CREATE MEMORY TABLE RROLE_RUSER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	ROLE_ID INTEGER NOT NULL,
	USER_ID INTEGER NOT NULL,
	CONSTRAINT FK_RROLE_RUSER_1 FOREIGN KEY(ROLE_ID) REFERENCES RROLE(ID),
	CONSTRAINT FK_RROLE_RUSER_2 FOREIGN KEY(USER_ID) REFERENCES RUSER(ID)
);

/* Misc */
CREATE MEMORY TABLE GLOBALPARAMETER
(
	ID INTEGER IDENTITY NOT NULL PRIMARY KEY,
	KEYNAME VARCHAR(50) NOT NULL,
	LASTMODIFIED TIMESTAMP NOT NULL,
	VALUE VARCHAR(1000) NOT NULL
);
