package it.nexbit.cuba.dynamiccolumns.entity;

import com.haulmont.chile.core.annotations.MetaClass;
import com.haulmont.chile.core.annotations.MetaProperty;
import javax.validation.constraints.NotNull;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.chile.core.annotations.NamePattern;

@NamePattern("%s|name")
@MetaClass(name = "nxdcol$DynamicColumn")
public class DynamicColumn extends BaseUuidEntity {
    private static final long serialVersionUID = 524180212151361861L;

    @NotNull
    @MetaProperty(mandatory = true)
    protected String name;

    @MetaProperty
    protected String groovyScript;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setGroovyScript(String groovyScript) {
        this.groovyScript = groovyScript;
    }

    public String getGroovyScript() {
        return groovyScript;
    }


}