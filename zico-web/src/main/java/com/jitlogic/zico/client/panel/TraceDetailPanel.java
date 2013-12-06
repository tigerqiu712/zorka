/**
 * Copyright 2012-2013 Rafal Lewczuk <rafal.lewczuk@jitlogic.com>
 * <p/>
 * This is free software. You can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p/>
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this software. If not, see <http://www.gnu.org/licenses/>.
 */
package com.jitlogic.zico.client.panel;


import com.google.gwt.cell.client.AbstractCell;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.HasHorizontalAlignment;
import com.google.inject.assistedinject.Assisted;
import com.jitlogic.zico.client.Resources;
import com.jitlogic.zico.client.api.TraceDataApi;
import com.jitlogic.zico.client.inject.PanelFactory;
import com.jitlogic.zico.data.TraceInfo;
import com.jitlogic.zico.data.TraceRecordInfo;
import com.jitlogic.zico.data.TraceRecordInfoProperties;
import com.sencha.gxt.core.client.IdentityValueProvider;
import com.sencha.gxt.data.shared.TreeStore;
import com.sencha.gxt.data.shared.loader.ChildTreeStoreBinding;
import com.sencha.gxt.data.shared.loader.DataProxy;
import com.sencha.gxt.data.shared.loader.TreeLoader;
import com.sencha.gxt.widget.core.client.button.TextButton;
import com.sencha.gxt.widget.core.client.container.VerticalLayoutContainer;
import com.sencha.gxt.widget.core.client.event.CellDoubleClickEvent;
import com.sencha.gxt.widget.core.client.event.SelectEvent;
import com.sencha.gxt.widget.core.client.form.NumberPropertyEditor;
import com.sencha.gxt.widget.core.client.form.SpinnerField;
import com.sencha.gxt.widget.core.client.grid.ColumnConfig;
import com.sencha.gxt.widget.core.client.grid.ColumnModel;
import com.sencha.gxt.widget.core.client.grid.RowExpander;
import com.sencha.gxt.widget.core.client.menu.Item;
import com.sencha.gxt.widget.core.client.menu.Menu;
import com.sencha.gxt.widget.core.client.menu.MenuItem;
import com.sencha.gxt.widget.core.client.toolbar.SeparatorToolItem;
import com.sencha.gxt.widget.core.client.toolbar.ToolBar;
import com.sencha.gxt.widget.core.client.treegrid.TreeGrid;
import org.fusesource.restygwt.client.Method;
import org.fusesource.restygwt.client.MethodCallback;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TraceDetailPanel extends VerticalLayoutContainer {

    private final static TraceRecordInfoProperties props = GWT.create(TraceRecordInfoProperties.class);

    private TraceDataApi tds;
    private TraceInfo traceInfo;
    private TreeGrid<TraceRecordInfo> methodTree;
    private TreeStore<TraceRecordInfo> methodTreeStore;
    private SpinnerField<Double> txtDuration;

    private TraceRecordSearchDialog searchDialog;

    private List<TraceRecordInfo> searchResults = new ArrayList<TraceRecordInfo>();
    private int currentResult = 0;

    private long minMethodTime = 0;

    private int expandLevel = -1;
    private int[] expandIndexes = null;
    private String expandPath = null;
    private TraceRecordInfo expandNode = null;
    private TextButton btnSearchPrev;
    private TextButton btnSearchNext;

    private PanelFactory panelFactory;


    @Inject
    public TraceDetailPanel(TraceDataApi tds, PanelFactory panelFactory, @Assisted TraceInfo traceInfo) {
        this.tds = tds;
        this.panelFactory = panelFactory;
        this.traceInfo = traceInfo;

        createToolbar();
        createTraceDetailTree();
        createContextMenu();
    }


    private void createToolbar() {
        ToolBar toolBar = new ToolBar();

        TextButton btnSlowestMethod = new TextButton();
        btnSlowestMethod.setIcon(Resources.INSTANCE.goDownIcon());
        btnSlowestMethod.setToolTip("Drill down: slowest method");
        toolBar.add(btnSlowestMethod);

        btnSlowestMethod.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                findNextSlowestMethod();
            }
        });

        toolBar.add(new SeparatorToolItem());

        TextButton btnFilter = new TextButton();
        btnFilter.setIcon(Resources.INSTANCE.clockIcon());
        btnFilter.setToolTip("Filter by execution time.");
        toolBar.add(btnFilter);

        txtDuration = new SpinnerField<Double>(new NumberPropertyEditor.DoublePropertyEditor());
        txtDuration.setIncrement(1d);
        txtDuration.setMinValue(0);
        txtDuration.setMaxValue(1000000d);
        txtDuration.setAllowNegative(false);
        txtDuration.setAllowBlank(true);
        txtDuration.setWidth(100);
        txtDuration.setToolTip("Minimum trace execution time (milliseconds)");
        toolBar.add(txtDuration);

        toolBar.add(new SeparatorToolItem());

        TextButton btnExpandAll = new TextButton();
        btnExpandAll.setIcon(Resources.INSTANCE.expandIcon());
        btnExpandAll.setToolTip("Expand all");
        toolBar.add(btnExpandAll);

        btnExpandAll.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                methodTree.expandAll();
            }
        });

        toolBar.add(new SeparatorToolItem());

        TextButton btnSearch = new TextButton();
        btnSearch.setIcon(Resources.INSTANCE.searchIcon());
        btnSearch.setToolTip("Search");
        toolBar.add(btnSearch);

        btnSearch.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                doSearch();
            }
        });

        btnSearchPrev = new TextButton();
        btnSearchPrev.setIcon(Resources.INSTANCE.goPrevIcon());
        btnSearchPrev.setToolTip("Search previous occurence");
        btnSearchPrev.setEnabled(false);
        toolBar.add(btnSearchPrev);

        btnSearchPrev.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                if (currentResult > 0) {
                    goToResult(currentResult - 1);
                }
            }
        });

        btnSearchNext = new TextButton();
        btnSearchNext.setIcon(Resources.INSTANCE.goNextIcon());
        btnSearchNext.setToolTip("Search next");
        btnSearchNext.setEnabled(false);

        btnSearchNext.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                if (currentResult < searchResults.size() - 1) {
                    goToResult(currentResult + 1);
                }
            }
        });

        toolBar.add(btnSearchNext);

        add(toolBar, new VerticalLayoutData(1, -1));

        btnFilter.addSelectHandler(new SelectEvent.SelectHandler() {
            @Override
            public void onSelect(SelectEvent event) {
                Double duration = txtDuration.getCurrentValue();
                minMethodTime = duration != null ? (long) (duration * 1000000) : 0;
                methodTree.collapseAll();
                List<TraceRecordInfo> tr = methodTreeStore.getRootItems();
                methodTreeStore.clear();
                methodTreeStore.add(tr);
            }
        });
    }

    private void doSearch() {
        if (searchDialog == null) {
            searchDialog = panelFactory.traceRecordSearchDialog(this, traceInfo);
        }
        searchDialog.show();
    }


    private void findNextSlowestMethod() {
        TraceRecordInfo info = null;


        for (TraceRecordInfo i : methodTreeStore.getAll()) {
            if ((info == null || i.getTime() > info.getTime()) &&
                    (!methodTree.isExpanded(i) && !methodTree.isLeaf(i))) {
                info = i;
            }
        }

        if (info != null) {
            methodTree.setExpanded(info, true);
            methodTree.getSelectionModel().setSelection(Arrays.asList(info));
            // TODO scroll to selected row if needed
        }

    }


    public void expandStart(String expandPath) {
        this.expandPath = expandPath.startsWith("/") ? expandPath : "/" + expandPath;
        // TODO this is a crutch; should search API always return full paths ?
        String[] s = this.expandPath.split("/");
        expandIndexes = new int[s.length];
        for (int i = 0; i < s.length; i++) {
            expandIndexes[i] = s[i].trim().length() > 0 ? Integer.parseInt(s[i]) : 0;
        }
        expandLevel = 0;
        expandNode = null;
        expandCont();
    }


    private void expandCont() {
        int idx = expandIndexes[expandLevel];
        expandNode = expandNode != null
                ? methodTreeStore.getChildren(expandNode).get(idx)
                : methodTreeStore.getRootItems().get(idx);

        expandLevel++;

        if (expandNode != null) {
            methodTree.getSelectionModel().setSelection(Arrays.asList(expandNode));
        }

        if (expandLevel >= expandIndexes.length || expandNode == null || methodTree.isLeaf(expandNode)) {
            expandPath = null;
            expandIndexes = null;
            expandNode = null;
            expandLevel = -1;
        } else {
            if (methodTree.isExpanded(expandNode)) {
                expandCont();
            } else {
                methodTree.setExpanded(expandNode, true);
            }
            //methodTree.getSelectionModel().setSelection(Arrays.asList(expandNode));
        }
    }


    public void setResults(List<TraceRecordInfo> results, int idx) {
        this.searchResults = results;
        goToResult(idx);
    }


    public void goToResult(int idx) {
        if (idx >= 0 && idx < searchResults.size()) {
            currentResult = idx;
            btnSearchNext.setEnabled(idx < searchResults.size() - 1);
            btnSearchPrev.setEnabled(idx > 0);
            expandStart(searchResults.get(idx).getPath());
        }
    }


    private void createTraceDetailTree() {

        DataProxy<TraceRecordInfo, List<TraceRecordInfo>> proxy = new DataProxy<TraceRecordInfo, List<TraceRecordInfo>>() {
            @Override
            public void load(TraceRecordInfo parent, final Callback<List<TraceRecordInfo>, Throwable> callback) {
                tds.listTraceRecords(traceInfo.getHostId(), traceInfo.getDataOffs(), minMethodTime,
                        parent != null ? parent.getPath() : null,
                        new MethodCallback<List<TraceRecordInfo>>() {
                            @Override
                            public void onFailure(Method method, Throwable exception) {
                                callback.onFailure(exception);
                            }

                            @Override
                            public void onSuccess(Method method, List<TraceRecordInfo> records) {
                                callback.onSuccess(records);
                                if (expandPath != null) {
                                    expandCont();
                                }
                            }
                        });
            }
        };


        final TreeLoader<TraceRecordInfo> loader = new TreeLoader<TraceRecordInfo>(proxy) {
            public boolean hasChildren(TraceRecordInfo info) {
                return info.getChildren() > 0;
            }
        };

        methodTreeStore = new TreeStore<TraceRecordInfo>(props.key());
        loader.addLoadHandler(new ChildTreeStoreBinding<TraceRecordInfo>(methodTreeStore));

        ColumnConfig<TraceRecordInfo, Long> durationCol = new ColumnConfig<TraceRecordInfo, Long>(props.time(), 50, "Time");
        durationCol.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        durationCol.setCell(new NanoTimeRenderingCell());
        durationCol.setMenuDisabled(true);
        durationCol.setSortable(false);


        ColumnConfig<TraceRecordInfo, Long> callsCol = new ColumnConfig<TraceRecordInfo, Long>(props.calls(), 50, "Calls");
        callsCol.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        callsCol.setMenuDisabled(true);
        callsCol.setSortable(false);

        ColumnConfig<TraceRecordInfo, Long> errorsCol = new ColumnConfig<TraceRecordInfo, Long>(props.errors(), 50, "Errors");
        errorsCol.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        errorsCol.setMenuDisabled(true);
        errorsCol.setSortable(false);

        ColumnConfig<TraceRecordInfo, Long> pctCol = new ColumnConfig<TraceRecordInfo, Long>(props.time(), 50, "Pct");
        pctCol.setAlignment(HasHorizontalAlignment.ALIGN_CENTER);
        pctCol.setMenuDisabled(true);
        pctCol.setSortable(false);

        pctCol.setCell(new AbstractCell<Long>() {
            @Override
            public void render(Context context, Long time, SafeHtmlBuilder sb) {
                double pct = 100.0 * time / traceInfo.getExecutionTime();
                String strTime = NumberFormat.getFormat("###.0").format(pct) + "%";
                sb.appendHtmlConstant("<span style=\"color: rgb(" + ((int) (pct * 2.49)) + ",0,0);\"><b>");
                sb.append(SafeHtmlUtils.fromString(strTime));
                sb.appendHtmlConstant("</b></span>");
            }
        });

        ColumnConfig<TraceRecordInfo, TraceRecordInfo> methodCol = new ColumnConfig<TraceRecordInfo, TraceRecordInfo>(
                new IdentityValueProvider<TraceRecordInfo>(), 500, "Method");

        methodCol.setMenuDisabled(true);
        methodCol.setSortable(false);

        methodCol.setCell(new AbstractCell<TraceRecordInfo>() {
            @Override
            public void render(Context context, TraceRecordInfo tr, SafeHtmlBuilder sb) {
                String color = tr.getExceptionInfo() != null ? "red"
                        : tr.getAttributes() != null ? "blue" : "black";
                sb.appendHtmlConstant("<span style=\"color: " + color + ";\">");
                sb.append(SafeHtmlUtils.fromString(tr.getMethod()));
                sb.appendHtmlConstant("</span>");
            }
        });

        RowExpander<TraceRecordInfo> expander = new RowExpander<TraceRecordInfo>(
                new IdentityValueProvider<TraceRecordInfo>(), new MethodDetailCell());

        ColumnModel<TraceRecordInfo> model = new ColumnModel<TraceRecordInfo>(
                Arrays.<ColumnConfig<TraceRecordInfo, ?>>asList(
                        expander, methodCol, durationCol, callsCol, errorsCol, pctCol));


        methodTree = new TreeGrid<TraceRecordInfo>(methodTreeStore, model, methodCol) {
            protected ImageResource calculateIconStyle(TraceRecordInfo model) {
                return null;
            }
        };

        methodTree.setBorders(true);
        methodTree.setTreeLoader(loader);
        methodTree.getView().setTrackMouseOver(false);
        methodTree.getView().setAutoExpandColumn(methodCol);
        methodTree.getView().setForceFit(true);

        methodTree.getStyle().setJointOpenIcon(Resources.INSTANCE.treePlusIcon());
        methodTree.getStyle().setJointCloseIcon(Resources.INSTANCE.treeMinusIcon());

        expander.initPlugin(methodTree);

        methodTree.addCellDoubleClickHandler(new CellDoubleClickEvent.CellDoubleClickHandler() {
            @Override
            public void onCellClick(CellDoubleClickEvent event) {
                TraceRecordInfo tr = methodTree.getSelectionModel().getSelectedItem();
                MethodAttrsDialog mad = panelFactory.methodAttrsDialog(
                        traceInfo.getHostId(), traceInfo.getDataOffs(), tr.getPath(), minMethodTime);
                mad.show();
            }
        });

        add(methodTree, new VerticalLayoutData(1, 1));
    }


    private void createContextMenu() {
        Menu menu = new Menu();


        MenuItem mnuSearchMethods = new MenuItem("Search for methods");
        mnuSearchMethods.setIcon(Resources.INSTANCE.searchIcon());
        menu.add(mnuSearchMethods);

        mnuSearchMethods.addSelectionHandler(new SelectionHandler<Item>() {
            @Override
            public void onSelection(SelectionEvent<Item> event) {
                TraceRecordInfo tr = methodTree.getSelectionModel().getSelectedItem();
                if (searchDialog == null) {
                    searchDialog = panelFactory.traceRecordSearchDialog(TraceDetailPanel.this, traceInfo);
                }
                searchDialog.setRootPath(tr != null ? tr.getPath() : "");
                searchDialog.show();
            }
        });


        MenuItem mnuMethodAttrs = new MenuItem("Method Details");
        mnuMethodAttrs.setIcon(Resources.INSTANCE.methodAttrsIcon());
        menu.add(mnuMethodAttrs);

        mnuMethodAttrs.addSelectionHandler(new SelectionHandler<Item>() {
            @Override
            public void onSelection(SelectionEvent<Item> event) {
                TraceRecordInfo tr = methodTree.getSelectionModel().getSelectedItem();
                MethodAttrsDialog dialog = panelFactory.methodAttrsDialog(
                        traceInfo.getHostId(), traceInfo.getDataOffs(), tr.getPath(), minMethodTime);
                dialog.show();
            }
        });

        methodTree.setContextMenu(menu);
    }
}
