/**
 * Copyright © 2013 enioka. All rights reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.enioka.jqm.client.api;

/**
 * The different status a {@link JobInstance} can take.
 */
public enum State {
    /** The job requests was just created. The {@link JobInstance} is waiting in queue. **/
    SUBMITTED,
    /** An engine has taken the {@link JobInstance} and will soon run it. JobInstance cannot be modified any more. **/
    ATTRIBUTED,
    /**
     * The payload is running. Check {@link JobInstance#getMessages()} and {@link JobInstance#getProgress()} to know what is happening
     * inside the payload.
     **/
    RUNNING,
    /** The payload has ended in error (it has thrown an exception). **/
    CRASHED,
    /** The payload has ended correctly (no exception). **/
    ENDED,
    /** The payload is still running but has received a KILL order. It may or may not obey it. **/
    KILLED,
    /** The execution request is still waiting inside the queue, but will not advance anymore. **/
    HOLDED,
    /** Reserved for future use. **/
    CANCELLED,
    /** Will become SUBMITTED when a set time is reached - it is a delayed launch. **/
    SCHEDULED
}
