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
package com.enioka.jqm.client.shared;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * <strong>Not part of any API - this an internal JQM class and may change without notice.</strong> <br>
 *
 * A file input stream overload which deletes the underlying file when the stream is closed.
 */
public class SelfDestructFileStream extends FileInputStream
{
    File f = null;
    public String nameHint = null;

    public SelfDestructFileStream(File file) throws FileNotFoundException
    {
        super(file);
        this.f = file;
    }

    @Override
    public void close() throws IOException
    {
        super.close();

        try
        {
            f.delete();
        }
        catch (Exception e)
        {
            // Nothing
        }
        f = null;
    }
}
