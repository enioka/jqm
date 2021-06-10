package com.enioka.jqm.jdbc;

public class DatabaseUnreachableException extends DatabaseException
{
    public DatabaseUnreachableException(Exception e)
    {
        super(e);
    }

    public DatabaseUnreachableException(String e)
    {
        super(e);
    }

    public DatabaseUnreachableException(String e, Exception ex)
    {
        super(e, ex);
    }
}
