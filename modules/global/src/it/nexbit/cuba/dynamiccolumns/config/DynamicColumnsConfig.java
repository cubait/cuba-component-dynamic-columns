package it.nexbit.cuba.dynamiccolumns.config;

import com.haulmont.cuba.core.config.Config;
import com.haulmont.cuba.core.config.Property;
import com.haulmont.cuba.core.config.Source;
import com.haulmont.cuba.core.config.SourceType;
import com.haulmont.cuba.core.config.defaults.DefaultString;

/**
 * Configuration parameters for the dynamic columns component.
 */
@Source(type = SourceType.APP)
public interface DynamicColumnsConfig extends Config {
    @Property("nxdcol.editActionShortcut")
    @DefaultString("CTRL-ALT-D")
    String getEditActionShortcut();
}
