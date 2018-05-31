package it.nexbit.cuba.dynamiccolumns.web.components;

import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.gui.components.Frame;
import com.haulmont.cuba.gui.components.Table;
import it.nexbit.cuba.dynamiccolumns.entity.DynamicColumn;
import it.nexbit.cuba.dynamiccolumns.web.components.actions.EditDynamicColumnsAction;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

public interface DynamicColumnsManager {
    String NAME = "nxdcol_DynamicColumnsManager";

    static DynamicColumnsManager create(Table target) {
        return AppBeans.getPrototype(NAME, target);
    }

    static void inject(Frame frame) {
        for (Field field : frame.getClass().getDeclaredFields()) {
            Annotation[] annotations = field.getAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation instanceof InjectDynamicColumnsAction) {
                    Object fieldValue;
                    try {
                        field.setAccessible(true);
                        fieldValue = field.get(frame);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Cannot access the value of " + field.getName() +
                                " field annotated by the @InjectDynamicColumnsAction attribute");
                    }
                    if (!(fieldValue instanceof Table)) {
                        throw new RuntimeException("The @InjectDynamicColumnsAction annotation may only be applied to fields of type Table");
                    }
                    Table table = (Table) fieldValue;
                    DynamicColumnsManager dcm = DynamicColumnsManager.create(table);
                    EditDynamicColumnsAction action = EditDynamicColumnsAction.create(dcm);
                    action.setCaptionDynamic(((InjectDynamicColumnsAction) annotation).dynamicCaption());
                    table.addAction(action);
                    dcm.updateTableDynamicColumns();
                }
            }
        }
    }

    /**
     * Get the current dynamic columns associated with the `target` table.
     *
     * @return  the dynamic columns defined for the target Table
     */
    List<DynamicColumn> getDynamicColumns();

    /**
     * Set the dynamic columns for the `target` table.
     *
     * @param dynamicColumns  the new dynamic columns for the target table
     */
    void setDynamicColumns(List<DynamicColumn> dynamicColumns);

    /**
     * Get the linked table managed by this instance (where the dynamic columns are attached).
     *
     * @return  the Table component linked with this manager
     */
    Table getTarget();

    /**
     * Set the linked table managed by this instance.
     *
     * <p>
     * This setter can be called only once after instance creation, then the target property
     * becomes read-only. Subsequent invocations will throw an {@link IllegalStateException}.
     * </p>
     *
     * @param target  the new Table component linked with this manager
     */
    void setTarget(Table target);

    /**
     * Get the unique name used to persist the user's setting containing the dynamic columns
     * configuration.<br><br>
     *
     * <p>
     * <b>Notes for implementors:</b><br>
     * <ul>
     *     <li>The returned string must be unique among: current user, target screen id, target
     *     table id</li>
     *     <li>If relying on the built-in {@link com.haulmont.cuba.security.app.UserSettingService}
     *     for the actual storage of the settings, the user should not be part of the string
     *     (use only a combination of screen id and table component id)</li>
     * </ul>
     * </p>
     *
     * @return  a name for the user's setting that must be unique among other settings
     */
    String getSettingName();

    /**
     * Update the dynamic columns on the target table.
     */
    void updateTableDynamicColumns();

    interface DynamicColumnGenerator extends Table.ColumnGenerator<Entity> {
        void setupDynamicColumn(Table table);
    }
}
