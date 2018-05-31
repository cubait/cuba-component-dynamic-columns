package it.nexbit.cuba.dynamiccolumns.web.screens;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.Range;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.MessageTools;
import com.haulmont.cuba.core.global.Metadata;
import com.haulmont.cuba.core.global.MetadataTools;
import com.haulmont.cuba.core.global.Scripting;
import com.haulmont.cuba.core.sys.jpql.DomainModel;
import com.haulmont.cuba.core.sys.jpql.model.EntityBuilder;
import com.haulmont.cuba.core.sys.jpql.model.JpqlEntityModel;
import com.haulmont.cuba.core.sys.jpql.model.JpqlEntityModelImpl;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.CreateAction;
import com.haulmont.cuba.gui.components.actions.EditAction;
import com.haulmont.cuba.gui.components.actions.RemoveAction;
import com.haulmont.cuba.gui.components.autocomplete.AutoCompleteSupport;
import com.haulmont.cuba.gui.components.autocomplete.JpqlSuggestionFactory;
import com.haulmont.cuba.gui.components.autocomplete.Suggester;
import com.haulmont.cuba.gui.components.autocomplete.Suggestion;
import com.haulmont.cuba.gui.components.autocomplete.impl.HintProvider;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.security.entity.EntityOp;
import it.nexbit.cuba.dynamiccolumns.entity.DynamicColumn;
import it.nexbit.cuba.dynamiccolumns.web.components.DynamicColumnsManager;
import it.nexbit.cuba.dynamiccolumns.web.components.actions.EditDynamicColumnsAction;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.control.CompilationFailedException;

import javax.inject.Inject;
import java.util.*;

public class DynamicColumnBrowse extends AbstractLookup implements EditDynamicColumnsAction.Dialog, Suggester {

    protected static final String ENTITY_NAME_ALIAS = "IT";

    /**
     * Indicates that a new instance of entity is being created.
     */
    protected boolean creating;

    /**
     * Indicates that the screen is in editing mode.
     */
    protected boolean editing;

    protected DynamicColumnsManager dynamicColumnsManager;

    protected Map<UUID, DynamicColumn> dynamicColumnsStore;

    @Inject
    protected Metadata metadata;

    @Inject
    protected MetadataTools metadataTools;

    @Inject
    protected MessageTools messageTools;

    @Inject
    protected Scripting scripting;

    @Inject
    protected VBoxLayout lookupBox;
    @Inject
    protected Table<DynamicColumn> table;
    @Inject
    protected TextField nameField;
    @Inject
    protected SourceCodeEditor groovyScriptField;
    @Inject
    protected VBoxLayout editBox;
    @Inject
    protected HBoxLayout actionsPane;
    @Inject
    private HBoxLayout dialogActionsPane;

    @Inject
    protected CollectionDatasource<DynamicColumn, UUID> dynamicColumnsDs;
    @Inject
    protected Datasource<DynamicColumn> dynamicColumnDs;

    @Override
    public void init(Map<String, Object> params) {
        initBrowseItemChangeListener();
        initBrowseCreateAction();
        initBrowseEditAction();
        initBrowseRemoveAction();
        initShortcuts();

        processParams(params);

        initControls();
        disableEditControls();
    }

    @SuppressWarnings("unchecked")
    protected void processParams(Map<String, Object> params) {
        dynamicColumnsStore = new HashMap<>();
        dynamicColumnsManager =
                (DynamicColumnsManager) params.get(EditDynamicColumnsAction.DYNAMICCOLUMNSMANAGER_PARAM);

        if (dynamicColumnsManager != null) {
            dynamicColumnsManager.getDynamicColumns().forEach(dc -> {
                dynamicColumnsStore.put(dc.getId(), dc);
                dynamicColumnsDs.includeItem(dc);
            });
        }
    }

    protected void initControls() {
        groovyScriptField.setSuggester(this);
    }

    /**
     * Adds a listener that reloads the selected record from the Map store
     */
    @SuppressWarnings("unchecked")
    protected void initBrowseItemChangeListener() {
        dynamicColumnsDs.addItemChangeListener(e -> {
            if (e.getItem() != null) {
                // put a copy of the original instance, to be able to reload it when cancelling edit
                dynamicColumnDs.setItem(metadataTools.copy(e.getItem()));
            }
        });
    }

    /**
     * Adds a CreateAction that removes selection in table, sets a newly created item to editDs
     * and enables controls for record editing.
     */
    protected void initBrowseCreateAction() {
        table.addAction(new CreateAction(table) {
            @SuppressWarnings("unchecked")
            @Override
            protected void internalOpenEditor(CollectionDatasource datasource, Entity newItem, Datasource parentDs, Map<String, Object> params) {
                initNewItem(newItem);
                table.setSelected(Collections.emptyList());
                initEditControls(newItem);
                enableEditControls(true);
            }
        });
    }

    /**
     * Hook to be implemented in subclasses. Called when the screen turns into editing mode
     * for a new entity instance. Enables additional initialization of the new entity instance
     * before setting it into the datasource.
     * @param item  new entity instance
     */
    protected void initNewItem(Entity item) {
    }

    @SuppressWarnings("unchecked")
    protected void initEditControls(Entity editEntity) {
        nameField.getDatasource().setItem(editEntity);
        groovyScriptField.getDatasource().setItem(editEntity);
    }

    /**
     * Adds an EditAction that enables controls for editing.
     */
    protected void initBrowseEditAction() {
        table.addAction(new EditAction(table) {
            @Override
            public void actionPerform(Component component) {
                if (table.getSelected().size() == 1) {
                    super.actionPerform(component);
                }
            }

            @Override
            protected void internalOpenEditor(CollectionDatasource datasource, Entity existingItem, Datasource parentDs, Map<String, Object> params) {
                enableEditControls(false);
            }

            @Override
            public void refreshState() {
                if (target != null) {
                    CollectionDatasource ds = target.getDatasource();
                    if (ds != null && !captionInitialized) {
                        setCaption(messages.getMainMessage("actions.Edit"));
                    }
                }
                super.refreshState();
            }

            @Override
            protected boolean isPermitted() {
                CollectionDatasource ownerDatasource = target.getDatasource();
                boolean entityOpPermitted = security.isEntityOpPermitted(ownerDatasource.getMetaClass(), EntityOp.UPDATE);
                if (!entityOpPermitted) {
                    return false;
                }
                return super.isPermitted();
            }
        });
    }

    /**
     * Adds AfterRemoveHandler for table's Remove action to reset the record contained in editDs.
     */
    @SuppressWarnings("unchecked")
    protected void initBrowseRemoveAction() {
        RemoveAction removeAction = (RemoveAction) table.getAction(RemoveAction.ACTION_ID);
        if (removeAction != null)
            removeAction.setAfterRemoveHandler((Set removedItems) -> {
                removedItems.forEach(item -> dynamicColumnsStore.remove(((Entity<UUID>) item).getId()));
                dynamicColumnDs.setItem(null);
            });
    }

    /**
     * Adds ESCAPE shortcut that invokes cancel() method.
     */
    protected void initShortcuts() {
        if (editBox != null) {
            editBox.addShortcutAction(
                    new ShortcutAction(new KeyCombination(KeyCombination.Key.ESCAPE),
                            shortcutTriggeredEvent -> cancel()));
        }
    }

    /**
     * Enables controls for editing.
     * @param creating indicates that a new instance is being created
     */
    protected void enableEditControls(boolean creating) {
        this.editing = true;
        this.creating = creating;
        initEditComponents(true);
        nameField.requestFocus();
    }

    /**
     * Disables edit controls.
     */
    protected void disableEditControls() {
        this.editing = false;
        initEditComponents(false);
        table.requestFocus();
    }

    /**
     * Initializes edit controls, depending on if they should be enabled or disabled.
     * @param enabled if true - enables edit controls and disables controls on the left side of the splitter
     *                if false - vice versa
     */
    protected void initEditComponents(boolean enabled) {
        nameField.setEditable(enabled);
        groovyScriptField.setEditable(enabled);
        actionsPane.setEnabled(enabled);
        dialogActionsPane.setEnabled(!enabled);
        lookupBox.setEnabled(!enabled);
    }

    /**
     * Method that is invoked by clicking Ok button after editing an existing or creating a new record.
     */
    @SuppressWarnings("unchecked")
    public void save() {
        if (!editing)
            return;

        List<Validatable> components = new ArrayList<>();
        components.add(nameField);
        components.add(groovyScriptField);
        if (!validate(components)) {
            return;
        }

        DynamicColumn editedItem = (DynamicColumn) nameField.getDatasource().getItem();
        if (creating) {
            dynamicColumnsDs.includeItem(editedItem);
        } else {
            dynamicColumnsDs.updateItem(editedItem);
        }
        dynamicColumnsStore.put(editedItem.getId(), editedItem);
        table.setSelected(editedItem);

        disableEditControls();
    }

    /**
     * Method that is invoked by clicking Cancel button, discards changes and disables controls for editing.
     */
    @SuppressWarnings("unchecked")
    public void cancel() {
        DynamicColumn selectedItem = dynamicColumnsDs.getItem();
        if (selectedItem == null) {
            dynamicColumnDs.setItem(null);
        } else {
            dynamicColumnDs.setItem(metadataTools.copy(selectedItem));
        }

        disableEditControls();
    }

    public void applyDialog() {
        getDsContext().commit();
        close(Window.COMMIT_ACTION_ID);
    }

    public void closeDialog() {
        close(Window.CLOSE_ACTION_ID);
    }

    public void testScript() {
        if (validateAll() && dynamicColumnsManager != null) {
            if (StringUtils.isNotBlank(groovyScriptField.getValue())) {
                Map<String, Object> context = new HashMap<>();
                context.put("__entity__",
                        metadata.create(dynamicColumnsManager.getTarget().getDatasource().getMetaClass()));
                try {
                    scripting.evaluateGroovy(
                            groovyScriptField.getValue().replace("{E}", "__entity__"),
                            context
                    );
                } catch (CompilationFailedException e) {
                    showMessageDialog(
                            getMessage("editDynamicColumsDialog.error"),
                            formatMessage("editDynamicColumsDialog.scriptCompilationError", e.toString()),
                            MessageType.WARNING_HTML
                    );
                    return;
                } catch (Exception e) {
                    // ignore
                }
            }

            showNotification(getMessage("editDynamicColumsDialog.scriptCorrect"),
                    NotificationType.HUMANIZED);
        }
    }

    @Override
    public Collection<DynamicColumn> getDynamicColumns() {
        return dynamicColumnsDs.getItems();
    }

    @Override
    public List<Suggestion> getSuggestions(AutoCompleteSupport source, String text, int cursorPosition) {
        int position;

        final MetaClass metaClass = dynamicColumnsManager.getTarget().getDatasource().getMetaClass();
        String query = "select " +
                text +
                " from " +
                metaClass.getName() +
                " " +
                ENTITY_NAME_ALIAS;
        //query = query.replace("{E}", ENTITY_NAME_ALIAS);
        position = "select ".length() + cursorPosition - 1;

        DomainModel domainModel = new DomainModel();
        EntityBuilder builder = new EntityBuilder();
        builder.startNewEntity(metaClass.getName());
        Collection<MetaProperty> props = metaClass.getProperties();
        for (MetaProperty prop : props) {
            if (metadataTools.isPersistent(prop))
                addProperty(builder, metaClass, prop);
        }
        JpqlEntityModel entity = builder.produce();
        ((JpqlEntityModelImpl) entity).setUserFriendlyName("IT");
        domainModel.add(entity);

        return JpqlSuggestionFactory.requestHint(query, position, source, cursorPosition, new HintProvider(domainModel));
    }

    private void addProperty(EntityBuilder builder, MetaClass metaClass, MetaProperty prop) {
        String name = prop.getName();
        String userFriendlyName = messageTools.getPropertyCaption(metaClass, prop.getName());
        boolean isEmbedded = metadataTools.isEmbedded(prop);
        MetaProperty.Type type = prop.getType();
        Class<?> javaType = prop.getJavaType();
        Range range = prop.getRange();
        switch (type) {
            case COMPOSITION:
            case ASSOCIATION:
                if (range.isClass()) {
                    MetaClass rangeClass = range.asClass();
                    if (range.getCardinality().isMany()) {
                        builder.addCollectionReferenceAttribute(name, rangeClass.getName(), userFriendlyName);
                    } else {
                        builder.addReferenceAttribute(name, rangeClass.getName(), userFriendlyName, isEmbedded);
                    }
                } else {
                    builder.addSingleValueAttribute(javaType, name, userFriendlyName);
                }
                break;
            case ENUM:
                //todo
                builder.addSingleValueAttribute(javaType, name, userFriendlyName);
                break;
            case DATATYPE:
                builder.addSingleValueAttribute(javaType, name, userFriendlyName);
                break;
        }
    }
}