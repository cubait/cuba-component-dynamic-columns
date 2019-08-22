package it.nexbit.cuba.dynamiccolumns.web.components.actions;

import com.haulmont.cuba.core.global.AppBeans;
import com.haulmont.cuba.core.global.Configuration;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.global.Security;
import com.haulmont.cuba.gui.WindowManager.OpenType;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.actions.ListAction;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.icons.CubaIcon;
import com.haulmont.cuba.security.entity.EntityOp;
import it.nexbit.cuba.dynamiccolumns.config.DynamicColumnsConfig;
import it.nexbit.cuba.dynamiccolumns.entity.DynamicColumn;
import it.nexbit.cuba.dynamiccolumns.web.components.DynamicColumnsManager;
import org.springframework.context.annotation.Scope;

import java.util.*;
import java.util.function.Supplier;

@org.springframework.stereotype.Component("nxdcol_EditDynamicColumnsAction")
@Scope("prototype")
public class EditDynamicColumnsAction extends ListAction
        implements Action.HasOpenType, Action.HasBeforeActionPerformedHandler {

    public static final String ACTION_ID = "editDynamicColumns";
    public static final String WINDOW_ID = "editDynamicColumnsDialog";
    public static final String DYNAMICCOLUMNSMANAGER_PARAM = "dynamicColumnsManager";
    public static final CubaIcon ICON = CubaIcon.COLUMNS;

    protected DynamicColumnsManager dynamicColumnsManager;
    protected OpenType openType;
    protected String windowId;
    protected boolean isCaptionDynamic;

    protected BeforeActionPerformedHandler beforeActionPerformedHandler;
    protected AfterWindowClosedHandler afterWindowClosedHandler;

    protected Map<String, Object> windowParams;
    protected Supplier<Map<String, Object>> windowParamsSupplier;

    protected Security security = AppBeans.get(Security.NAME);

    private Messages messages = AppBeans.get(Messages.NAME);

    /**
     * Creates an action with default id, opening the edit screen in a DIALOG.
     * @param manager    the DynamicColumnsManager linked to the target table
     */
    public static EditDynamicColumnsAction create(DynamicColumnsManager manager) {
        return AppBeans.getPrototype("nxdcol_EditDynamicColumnsAction", manager);
    }

    /**
     * Creates an action with default id.
     * @param manager   the DynamicColumnsManager linked to the target table
     * @param openType  how to open the edit screen
     */
    public static EditDynamicColumnsAction create(DynamicColumnsManager manager, OpenType openType) {
        return AppBeans.getPrototype("nxdcol_EditDynamicColumnsAction", manager, openType);
    }

    /**
     * Creates an action with the given id.
     * @param manager   the DynamicColumnsManager linked to the target table
     * @param openType  how to open the edit screen
     * @param id        action name
     */
    public static EditDynamicColumnsAction create(DynamicColumnsManager manager, OpenType openType, String id) {
        return AppBeans.getPrototype("nxdcol_EditDynamicColumnsAction", manager, openType, id);
    }

    /**
     * Constructor that uses the default action id (ACTION_ID), and opens the edit screen in a
     * DIALOG.
     * @param manager   the DynamicColumnsManager linked to the target table
     */
    public EditDynamicColumnsAction(DynamicColumnsManager manager) {
        this(manager, OpenType.DIALOG, ACTION_ID);
    }

    /**
     * Constructor that uses the default action id (ACTION_ID), but specify how to open the edit
     * screen.
     * @param manager   the DynamicColumnsManager linked to the target table
     * @param openType  how to open the edit screen
     */
    public EditDynamicColumnsAction(DynamicColumnsManager manager, OpenType openType) {
        this(manager, openType, ACTION_ID);
    }

    /**
     * Constructor that allows to specify action's name.
     * @param manager   the DynamicColumnsManager linked to the target table
     * @param openType  how to open the edit screen
     * @param id        action's identifier
     */
    public EditDynamicColumnsAction(DynamicColumnsManager manager, OpenType openType, String id) {
        super(id);
        this.dynamicColumnsManager = manager;
        this.target = manager.getTarget();
        this.openType = openType;
        this.windowId = WINDOW_ID;
        this.caption = messages.getMainMessage("actions.EditDynamicColumns");
        this.setIconFromSet(ICON);

        Configuration configuration = AppBeans.get(Configuration.NAME);
        DynamicColumnsConfig config = configuration.getConfig(DynamicColumnsConfig.class);
        setShortcut(config.getEditActionShortcut());
    }

    /**
     * Check permissions for action (by default it is permitted only if user has READ permission
     * on target's datasource entity, and target is an instance of Table)
     */
    @Override
    protected boolean isPermitted() {
        if (target == null || target.getDatasource() == null || !(target instanceof Table)) {
            return false;
        }

        CollectionDatasource ownerDatasource = target.getDatasource();
        boolean entityOpPermitted = security.isEntityOpPermitted(ownerDatasource.getMetaClass(), EntityOp.READ);
        if (!entityOpPermitted) {
            return false;
        }

        return super.isPermitted();
    }

    @Override
    public void actionPerform(Component component) {
        if (beforeActionPerformedHandler != null) {
            if (!beforeActionPerformedHandler.beforeActionPerformed())
                return;
        }

        // show the edit columns dialog
        Map<String, Object> params = prepareWindowParams();
        internalOpenDialog(params);
    }

    @Override
    public void refreshState() {
        super.refreshState();
        updateCaption();
    }

    protected void updateCaption() {
        if (isCaptionDynamic) {
            Integer count = dynamicColumnsManager.getDynamicColumns().size();
            setCaption(count.toString());
        }
    }

    protected String getSettingName() {
        String windowId = target.getFrame().getId();
        String tableId = target.getId();
        return String.format("nxdcol_%s_%s",
                windowId, tableId);
    }

    protected Map<String, Object> prepareWindowParams() {
        Map<String, Object> windowParams = getWindowParams();
        Map<String, Object> supplierParams = null;
        if (windowParamsSupplier != null) {
            supplierParams = windowParamsSupplier.get();
        }

        Map<String, Object> params = new HashMap<>();
        params.put(DYNAMICCOLUMNSMANAGER_PARAM, dynamicColumnsManager);
        if (supplierParams != null || windowParams != null) {
            params.putAll(windowParams != null ? windowParams : Collections.emptyMap());
            params.putAll(supplierParams != null ? supplierParams : Collections.emptyMap());
        }
        return params;
    }

    protected void internalOpenDialog(Map<String, Object> params) {
        Dialog dialog = (Dialog) target.getFrame().openWindow(getWindowId(), getOpenType(), params);

        dialog.addCloseListener(actionId -> {
            // move focus to owner
            target.requestFocus();

            if (Window.COMMIT_ACTION_ID.equals(actionId)) {
                Collection<DynamicColumn> editedCols = dialog.getDynamicColumns();
                // save the new dynamic columns and update the target table
                dynamicColumnsManager.setDynamicColumns(new ArrayList<>(editedCols));
                updateCaption();
            }

            afterWindowClosed(dialog, actionId);
            if (afterWindowClosedHandler != null) {
                afterWindowClosedHandler.handle(dialog, actionId);
            }
        });
    }

    /**
     * @return  edit dialog parameters
     */
    public Map<String, Object> getWindowParams() {
        return windowParams;
    }

    /**
     * @param windowParams edit dialog parameters
     */
    public void setWindowParams(Map<String, Object> windowParams) {
        this.windowParams = windowParams;
    }

    /**
     * @return supplier that provides edit dialog parameters
     */
    public Supplier<Map<String, Object>> getWindowParamsSupplier() {
        return windowParamsSupplier;
    }

    /**
     * @param windowParamsSupplier supplier that provides editor screen parameters
     */
    public void setWindowParamsSupplier(Supplier<Map<String, Object>> windowParamsSupplier) {
        this.windowParamsSupplier = windowParamsSupplier;
    }

    /**
     * Hook invoked always after the edit window was closed
     * @param window         the edit window
     * @param closeActionId  the id of the action that lead to closing
     */
    protected void afterWindowClosed(Window window, String closeActionId) {
    }

    /**
     *
     * @return the hadler invoked after the window is closed
     */
    public AfterWindowClosedHandler getAfterWindowClosedHandler() {
        return afterWindowClosedHandler;
    }

    /**
     * @param afterWindowClosedHandler handler that is always invoked after the window is closed
     */
    public void setAfterWindowClosedHandler(AfterWindowClosedHandler afterWindowClosedHandler) {
        this.afterWindowClosedHandler = afterWindowClosedHandler;
    }

    /**
     * @return  editor screen open type
     */
    @Override
    public OpenType getOpenType() {
        return openType;
    }

    /**
     * @param openType  editor screen open type
     */
    @Override
    public void setOpenType(OpenType openType) {
        this.openType = openType;
    }

    public String getWindowId() {
        return windowId;
    }

    public void setWindowId(String windowId) {
        this.windowId = windowId;
    }

    @Override
    public BeforeActionPerformedHandler getBeforeActionPerformedHandler() {
        return beforeActionPerformedHandler;
    }

    @Override
    public void setBeforeActionPerformedHandler(BeforeActionPerformedHandler handler) {
        this.beforeActionPerformedHandler = handler;
    }

    public DynamicColumnsManager getDynamicColumnsManager() {
        return dynamicColumnsManager;
    }

    public void setDynamicColumnsManager(DynamicColumnsManager manager) {
        if (this.dynamicColumnsManager != manager) {
            this.dynamicColumnsManager = manager;
            this.setTarget(manager.getTarget());
        }
    }

    /**
     * Set the new target component for this action.
     *
     * @param target  the new Table target
     *
     * @throws IllegalArgumentException  if target was not a {@link Table} component instance
     */
    @Override
    public void setTarget(ListComponent target) {
        if (this.target == target) {
            return;
        }
        if (target instanceof Table) {
            dynamicColumnsManager.setTarget((Table) target);
            super.setTarget(target);
        } else {
            throw new IllegalArgumentException("target must be a Table component");
        }
    }

    public boolean isCaptionDynamic() {
        return isCaptionDynamic;
    }

    public void setCaptionDynamic(boolean captionDynamic) {
        if (this.isCaptionDynamic != captionDynamic) {
            this.isCaptionDynamic = captionDynamic;

            refreshState();
        }
    }


    public interface AfterWindowClosedHandler {
        /**
         * @param dialog        the dialog window
         * @param closeActionId ID of action caused the screen closing
         */
        void handle(Dialog dialog, String closeActionId);
    }

    public interface Dialog extends Window {
        Collection<DynamicColumn> getDynamicColumns();
    }
}
