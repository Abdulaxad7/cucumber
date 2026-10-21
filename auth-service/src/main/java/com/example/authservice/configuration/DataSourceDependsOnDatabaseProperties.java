package com.example.authservice.configuration;

import org.springframework.boot.autoconfigure.AbstractDependsOnBeanFactoryPostProcessor;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class DataSourceDependsOnDatabaseProperties extends AbstractDependsOnBeanFactoryPostProcessor {

    public DataSourceDependsOnDatabaseProperties() {
        super(DataSource.class, DatabaseSettingsCheck.class);
    }
}
