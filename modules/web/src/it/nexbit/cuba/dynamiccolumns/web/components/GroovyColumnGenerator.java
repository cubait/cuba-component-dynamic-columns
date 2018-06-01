package it.nexbit.cuba.dynamiccolumns.web.components;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Scripting;
import com.haulmont.cuba.gui.components.Label;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.xml.layout.ComponentsFactory;
import groovy.lang.Binding;
import it.nexbit.cuba.dynamiccolumns.entity.DynamicColumn;

public class GroovyColumnGenerator implements DynamicColumnsManager.DynamicColumnGenerator {

    protected Scripting scripting = AppBeans.get(Scripting.NAME);
    protected ComponentsFactory componentsFactory = AppBeans.get(ComponentsFactory.NAME);

    protected DynamicColumn dynamicColumn;

    public GroovyColumnGenerator(DynamicColumn dynamicColumn) {
        this.dynamicColumn = dynamicColumn;
    }

    @SuppressWarnings("unchecked")
    public void setupDynamicColumn(Table table) {
        table.addGeneratedColumn(dynamicColumn.getId().toString(), this);
        table.getColumn(dynamicColumn.getId().toString()).setCaption(dynamicColumn.getName());
    }

    /**
     * Called by {@link Table} when rendering a column for which the generator was created.
     *
     * @param entity an entity instance represented by the current row
     * @return a component to be rendered inside of the cell
     */
    @Override
    public com.haulmont.cuba.gui.components.Component generateCell(Entity entity) {
        Binding binding = new Binding();
        binding.setVariable("__entity__", entity);
        try {
            Object result = scripting.evaluateGroovy(parseScript(), binding);
            return new Table.PlainTextCell(result == null ? "" : result.toString());
        } catch (RuntimeException e) {
            Label errorLabel = (Label) componentsFactory.createComponent(Label.NAME);
            errorLabel.setValue(e.getMessage());
            errorLabel.setStyleName("failure");
            return errorLabel;
        }
    }

    protected String parseScript() {
        return dynamicColumn.getGroovyScript().replace("{E}", "__entity__");
    }
}
