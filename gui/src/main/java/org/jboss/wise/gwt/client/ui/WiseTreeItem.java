package org.jboss.wise.gwt.client.ui;

import com.google.gwt.user.client.ui.*;
import org.jboss.wise.gwt.shared.tree.element.EnumerationTreeElement;
import org.jboss.wise.gwt.shared.tree.element.SimpleTreeElement;
import org.jboss.wise.gwt.shared.tree.element.TreeElement;

import java.util.Iterator;

/**
 * User: rsearls
 * Date: 5/23/15
 */
public class WiseTreeItem extends TreeItem {

    public static final String CSS_ENABLEBLK = "enableBlk";
    public static final String CSS_DISABLEBLK = "disableBlk";

    private TreeElement wTreeElement = null;
    private SimpleCheckBox checkBox = null;
    private FocusWidget inputBox = null;
    private boolean validationError = false;

    public WiseTreeItem() {
    }

    public WiseTreeItem(Widget widget) {
        super(widget);
    }

    public TreeElement getWTreeElement() {

        return wTreeElement;
    }

    public void setWTreeElement(TreeElement wTreeElement) {

        this.wTreeElement = wTreeElement;
    }

    public boolean isValidationError() {
        return validationError;
    }

    public void setValidationError(boolean flag) {
        validationError = flag;
    }

    public SimpleCheckBox getCheckBox() {
        return checkBox;
    }

    public TextBox getTextBox() {
        if (inputBox instanceof TextBox) {
            return (TextBox) inputBox;
        }
        return null;
    }

    /**
     * @param enable
     */
    public void setEnableTreeItem(boolean enable) {

        String styleName = (enable) ? CSS_ENABLEBLK : CSS_DISABLEBLK;

        Widget widget = getWidget();

        if (widget instanceof ComplexPanel) {
            Iterator<Widget> itWidget = ((ComplexPanel) widget).iterator();
            while (itWidget.hasNext()) {
                Widget w = itWidget.next();
                if (w instanceof FocusWidget) {
                    ((FocusWidget) w).setEnabled(enable);
                    ((FocusWidget) w).setStyleName(styleName);

                    if (w instanceof SimpleCheckBox) {
                        wTreeElement.setNil(!enable);
                    }

                } else if (w instanceof LabelBase) {
                    w.setStyleName(styleName);
                }
            }
        }

    }

    /**
     * Extract components for future ref
     */
    public void postCreateProcess() {
        Widget widget = getWidget();

        if (widget instanceof ComplexPanel) {

            Iterator<Widget> itWidget = ((ComplexPanel) widget).iterator();
            while (itWidget.hasNext()) {
                Widget w = itWidget.next();

                if (w instanceof SimpleCheckBox) {
                    checkBox = (SimpleCheckBox) w;
                } else if (w instanceof ValueBoxBase) {
                    inputBox = (FocusWidget) w;
                } else if (w instanceof ListBox) {
                    inputBox = (FocusWidget) w;
                }
            }
        }
    }

    public void postProcess() {
        // special eval of items using LeafKeyUpHandler required

        if (inputBox == null) {
            int cnt = getChildCount();
            for (int i = 0; i < cnt; i++) {
                ((WiseTreeItem) getChild(i)).postProcess();
            }
        } else {

            if (inputBox instanceof TextBox) {
                if (checkBox != null) {
                    wTreeElement.setNil(!checkBox.getValue());
                }

                if (!wTreeElement.isNil()) {
                    setWidgetValue(inputBox, wTreeElement);

                    int cnt = getChildCount();
                    for (int i = 0; i < cnt; i++) {
                        ((WiseTreeItem) getChild(i)).postProcess();
                    }
                }
                // work around: setWidgetValue seems to override the initial setting, reset value
                if (checkBox != null) {
                    wTreeElement.setNil(!checkBox.getValue());
                }

            } else {
                setWidgetValue(inputBox, wTreeElement);
            }
        }
    }

    private void setWidgetValue(Widget widget, TreeElement pNode) {

        if (pNode instanceof EnumerationTreeElement) {
            ListBox lBox = (ListBox) widget;
            EnumerationTreeElement eNode = (EnumerationTreeElement) pNode;

            int index = lBox.getSelectedIndex();
            eNode.setValue(lBox.getItemText(index));

        } else {

            if (widget instanceof TextBox) {
                String s = ((TextBox) widget).getValue();
                if (s != null) {
                    ((SimpleTreeElement) pNode).setValue(s);
                    ((SimpleTreeElement) pNode).setNil(false);
                }

            } else if (widget instanceof IntegerBox) {
                Integer i = ((IntegerBox) widget).getValue();
                if (i != null) {
                    ((SimpleTreeElement) pNode).setValue(i.toString());
                }

            } else if (widget instanceof DoubleBox) {
                Double d = ((DoubleBox) widget).getValue();
                if (d != null) {
                    ((SimpleTreeElement) pNode).setValue(d.toString());
                }
            }
        }
    }

}
