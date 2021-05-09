package com.enioka.jqm.cli;

import java.io.File;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Simple file read check. Must be public to be instanciated by JCommander.
 */
public class ValidatorFileCanRead implements IParameterValidator
{
    @Override
    public void validate(String name, String value) throws ParameterException
    {
        File f = new File(value);
        if (!f.exists())
        {
            throw new ParameterException("File " + value + " does not exist");
        }
        if (!f.canRead())
        {
            throw new ParameterException("File " + value + " exists but cannot be read");
        }
        if (f.isDirectory() && !f.canExecute())
        {
            throw new ParameterException("Directory " + value + " exists but cannot be opened");
        }
    }
}
