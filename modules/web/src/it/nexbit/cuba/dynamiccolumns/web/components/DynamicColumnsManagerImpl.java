package it.nexbit.cuba.dynamiccolumns.web.components;

import com.google.common.collect.ImmutableList;
import com.haulmont.bali.util.Dom4j;
import com.haulmont.cuba.core.entity.BaseUuidEntity;
import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.MetadataTools;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.security.app.UserSettingService;
import it.nexbit.cuba.dynamiccolumns.entity.DynamicColumn;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component(DynamicColumnsManager.NAME)
@Scope("prototype")
public class DynamicColumnsManagerImpl implements DynamicColumnsManager {

    protected List<DynamicColumn> dynamicColumns;

    protected List<DynamicColumn> previousDynamicColumns;

    protected Table target;

    protected Map<UUID, DynamicColumnGenerator> columnGeneratorsStore;

    protected UserSettingService settings = AppBeans.get(UserSettingService.NAME);
    protected Metadata metadata = AppBeans.get(Metadata.NAME);
    protected MetadataTools metadataTools = AppBeans.get(MetadataTools.NAME);

    public DynamicColumnsManagerImpl(Table target) {
        this.target = Objects.requireNonNull(target);

        this.columnGeneratorsStore = new HashMap<>();
    }

    /**
     * Get the current dynamic columns associated with the `target` table.
     *
     * <p>
     *     This is a lazy-loaded property: it first loads the dynamic columns configuration from
     *     user's settings the first time it is invoked (using
     *     {@link #loadDynamicColumnsFromUserSetting()} method).<br>
     *     After the first load, the settings are cached in memory, and never loaded from
     *     persistent storage again. To force reloading from persistent storage, call the
     *     {@link #reset()} method.
     * </p>
     *
     * @return the dynamic columns defined for the target Table, as an immutable list
     */
    @Override
    public List<DynamicColumn> getDynamicColumns() {
        if (dynamicColumns == null) {
            dynamicColumns = ImmutableList.copyOf(loadDynamicColumnsFromUserSetting());
        }
        return dynamicColumns;
    }

    /**
     * Set the dynamic columns for the `target` table.
     *
     * <p>
     *     Setting this property always trigger the saving of the passed dynamic columns into the
     *     persistent user's settings.<br>
     *     It also trigger the update of the target table columns, by calling the
     *     {@link #updateTableDynamicColumns()} method.
     * </p>
     *
     * @param dynamicColumns the new dynamic columns for the target table
     */
    @Override
    public void setDynamicColumns(List<DynamicColumn> dynamicColumns) {
        if (dynamicColumns == null) {
            this.dynamicColumns = ImmutableList.of();
        } else {
            this.dynamicColumns = ImmutableList.copyOf(dynamicColumns);
        }
        saveDynamicColumnsToUserSetting(this.dynamicColumns);
        updateTableDynamicColumns();
    }

    /**
     * Get the linked table managed by this instance (where the dynamic columns are attached).
     *
     * @return the Table component linked with this manager
     */
    @Override
    public Table getTarget() {
        return target;
    }

    /**
     * Set the linked table managed by this instance.
     *
     * <p>
     * This setter can be called only once after instance creation, then the target property
     * becomes read-only. Subsequent invocations will throw an {@link IllegalStateException}.
     * </p>
     *
     * @param target  the new Table component linked with this manager
     *
     * @throws NullPointerException   target was null
     * @throws IllegalStateException  target was already set
     */
    @Override
    public void setTarget(Table target) {
        if (this.target == target) {
            return;
        }
        if (this.target != null) {
            throw new IllegalStateException("target is already set");
        }
        this.target = Objects.requireNonNull(target);
    }

    /**
     * Get the unique name used to persist the user's setting containing the dynamic columns
     * configuration.<br><br>
     *
     * <p>
     * <b>Note:</b> The returned string is composed by concatenating the constant "nxdcol" with the
     * target's container frame id and the target's id, by using the '_' character as separator.
     * </p>
     *
     * @return a name for the user's setting that must be unique among other settings
     */
    @Override
    public String getSettingName() {
        String windowId = getTarget().getFrame().getId();
        String tableId = getTarget().getId();
        return String.format("nxdcol_%s_%s",
                windowId, tableId);
    }

    /**
     * Update the dynamic columns on the target table.
     *
     * <p>
     * <b>NOTE:</b> If the table already contains dynamic
     * columns with the same id, those are always removed first and then re-added, even if they
     * were not modified.
     * </p>
     *
     * @throws IllegalStateException  target was not set yet
     */
    @Override
    public void updateTableDynamicColumns() {
        if (getTarget() == null) {
            throw new IllegalStateException("target has not been set");
        }

        List<DynamicColumn> columns = getDynamicColumns();

        if (previousDynamicColumns != null) {
            previousDynamicColumns.stream()
                    .filter(dc -> !columns.contains(dc))
                    .map(BaseUuidEntity::getId)
                    .forEach(columnId -> {
                        getTarget().removeGeneratedColumn(columnId.toString());
                        columnGeneratorsStore.remove(columnId);
                    });
        }

        for (DynamicColumn dc : columns) {
            // if a generated column with the same id already exists, it will be replaced
            DynamicColumnGenerator generator = new GroovyColumnGenerator(dc);
            columnGeneratorsStore.put(dc.getId(), generator);
            generator.setupDynamicColumn(target);
        }

        // makes a deep clone of the current columns to be able to track column removals for the
        // next method invocation
        if (columns.size() > 0) {
            this.previousDynamicColumns = columns.stream()
                    .map(dc -> metadataTools.copy(dc))
                    .collect(Collectors.toList());
        } else {
            this.previousDynamicColumns = null;
        }
    }

    /**
     * Reset the dynamic columns configuration held by the instance, thus forcing a reload from
     * persistent storage the next time {@link #getDynamicColumns()} is invoked.<br><br>
     *
     * <p>
     * <b>Note:</b>
     * It does not invoke {@link #updateTableDynamicColumns()} automatically.
     * </p>
     */
    public void reset() {
        this.dynamicColumns = null;
    }

    protected List<DynamicColumn> loadDynamicColumnsFromUserSetting() {
        String setting = settings.loadSetting(getSettingName());
        if (StringUtils.isBlank(setting)) {
            return ImmutableList.of();
        }

        Element root = Dom4j.readDocument(setting).getRootElement();
        if (!root.getName().equals("nxdcol")) {
            throw new IllegalStateException("Invalid settings detected");
        }
        Element columnsRoot = root.element("columns");
        if (columnsRoot == null) {
            columnsRoot = root.addElement("columns");
        }

        List<DynamicColumn> columns = new ArrayList<>();

        for (Element e : columnsRoot.elements("column")) {
            DynamicColumn dc = deserializeDynamicColumnElement(e);
            if (dc != null) {
                columns.add(dc);
            }
        }

        return columns;
    }

    protected DynamicColumn deserializeDynamicColumnElement(Element element) {
        if (StringUtils.isNotBlank(element.attributeValue("id"))) {
            UUID uuid = UUID.fromString(element.attributeValue("id"));
            String name = element.attributeValue("name");
            String groovyScript = element.attributeValue("groovyScript");
            DynamicColumn dc = (DynamicColumn) metadata.create("nxdcol$DynamicColumn");
            dc.setId(uuid);
            dc.setName(name);
            dc.setGroovyScript(groovyScript);
            return dc;
        }
        return null;
    }

    protected void saveDynamicColumnsToUserSetting(Collection<DynamicColumn> columns) {
        Element root = DocumentHelper.createDocument().addElement("nxdcol");
        Element columnsRoot = root.addElement("columns");
        for (DynamicColumn dc : columns) {
            serializeDynamicColumnElement(columnsRoot.addElement("column"), dc);
        }
        settings.saveSetting(getSettingName(), Dom4j.writeDocument(root.getDocument(), true));
    }

    protected Element serializeDynamicColumnElement(Element element, DynamicColumn dynamicColumn) {
        return element
                .addAttribute("id", dynamicColumn.getId().toString())
                .addAttribute("name", dynamicColumn.getName())
                .addAttribute("groovyScript", dynamicColumn.getGroovyScript());
    }
}

