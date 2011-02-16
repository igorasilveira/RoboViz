/*
 *  Copyright 2011 RoboViz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rv.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import rv.comm.drawing.Drawings;
import rv.comm.drawing.Drawings.SetListChangeEvent;
import rv.comm.drawing.Drawings.ShapeListListener;
import rv.comm.drawing.ShapeSet;

/**
 * TODO: lots of work on this class; should use a JTree instead of JList
 * 
 * @author justin
 * 
 */
public class PoolListPanel extends JPanel implements ShapeListListener {

    static class CheckListRenderer extends JCheckBox implements
            ListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList list, Object value,
                int index, boolean isSelected, boolean hasFocus) {
            setEnabled(list.isEnabled());
            setSelected(((CheckListItem) value).isSelected());
            setFont(list.getFont());
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            setText(value.toString());
            return this;
        }
    }

    static class CheckListItem {
        private ShapeSet pool;
        private String   label;
        private boolean  isSelected = false;

        public CheckListItem(ShapeSet pool) {
            this.pool = pool;
            this.label = pool.getName();
        }

        public boolean isSelected() {
            return isSelected;
        }

        public void setSelected(boolean isSelected) {
            this.isSelected = isSelected;
            pool.setVisible(isSelected);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private class Cell extends JComponent {
        private String    pool;
        private JLabel    text;
        private JCheckBox checkbox;

        public Cell(String text, boolean state) {
            this.pool = text;
            this.text = new JLabel(text);
            this.checkbox = new JCheckBox();
            checkbox.setSelected(state);
            checkbox.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ShapeSet p = drawings.getShapeSet(pool);
                    if (p != null)
                        p.setVisible(!p.isVisible());
                }
            });

            this.setLayout(new GridLayout(0, 2));
            add(this.text);
            add(this.checkbox);
        }
    }

    private Drawings       drawings;
    private JFrame         poolFrame;
    private JTextField     regexField;
    private JList          list;
    final DefaultListModel model = new DefaultListModel();

    public void showFrame() {
        poolFrame.setVisible(true);
    }

    public PoolListPanel(Drawings drawings) {

        poolFrame = new JFrame("Drawings");
        poolFrame.setAlwaysOnTop(true);
        list = new JList(model);

        list.setCellRenderer(new CheckListRenderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                // TODO: try/catch really needed?
                try {
                    JList list = (JList) event.getSource();
                    int index = list.locationToIndex(event.getPoint());
                    CheckListItem item = (CheckListItem) list.getModel()
                            .getElementAt(index);
                    item.setSelected(!item.isSelected());
                    list.repaint(list.getCellBounds(index, index));
                } catch (Exception e) {
                }
            }
        });

        poolFrame.setLayout(new BorderLayout());

        poolFrame.add(new JScrollPane(list), BorderLayout.CENTER);
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(1, 2));
        regexField = new JTextField();

        p.add(regexField);
        JButton regexSearch = new JButton("Regex");
        regexSearch.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                regexList(regexField.getText());
            }
        });
        p.add(regexSearch);
        regexField.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    regexList(regexField.getText());
            }
        });
        poolFrame.add(p, BorderLayout.SOUTH);

        this.drawings = drawings;
        drawings.addShapeSetListener(this);

        poolFrame.pack();
        // TODO: shouldnt do this, just grab pools on init
        drawings.clearAllShapeSets();
    }

    private void regexList(String s) {
        for (int i = 0; i < model.getSize(); i++) {
            CheckListItem cli = ((CheckListItem) model.getElementAt(i));
            cli.setSelected(cli.pool.getName().matches(s));
        }
        list.repaint();
    }

    @Override
    public void setListChanged(SetListChangeEvent evt) {
        // TODO Sort
        model.clear();
        ArrayList<ShapeSet> pools = evt.getSets();
        int size = pools.size();
        for (int i = 0; i < size; i++) {
            if (pools.get(i) != null) {
                CheckListItem item = new CheckListItem(pools.get(i));
                item.setSelected(pools.get(i).isVisible());
                model.addElement(item);
            }
        }
    }
}
