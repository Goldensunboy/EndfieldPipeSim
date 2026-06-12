import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.swing.filechooser.FileNameExtensionFilter;

public class PipeSim extends JPanel {
    public static PipeSim INSTANCE;
    public static final int GRID_SIZE = 20;
    public static final int PANEL_SIZE = 800;
    public static final Color BG_COLOR = new Color(230, 230, 230);

    public AssetFactory assets = new AssetFactory();
    public ArrayList<PipeComponent> pipeComponents = new ArrayList<>();
    public Simulation sim;
    public PipeComponent addingPipeComponent = null;
    public Point lastMousePos = new Point(-1, -1);
    public Point selectionBoxStart = null, selectionBoxEnd = null;
    public boolean movingSelection = false;
    private volatile Thread playThread = null;
    private volatile boolean playStopRequested = false;

    /**
     * Application entry point.
     *
     * @param args CLI arguments (unused)
     */
    public static void main(String[] args) {
        JFrame frame = new JFrame("PipeSim");
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));
        frame.setContentPane(contentPanel);

        PipeSim drawPanel = new PipeSim();
        frame.setJMenuBar(drawPanel.createMenuBar());

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.PIPE));
        controlPanel.add(new JLabel("Pipe"));
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.CURVED_PIPE));
        controlPanel.add(new JLabel("Curved Pipe"));
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.BRIDGE));
        controlPanel.add(new JLabel("Bridge"));
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.SPLITTER));
        controlPanel.add(new JLabel("Splitter"));
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.CONVERGER));
        controlPanel.add(new JLabel("Converger"));
        controlPanel.add(Box.createVerticalStrut(10));
        controlPanel.add(new ImageButton(PipeComponent.ComponentType.TANK));
        controlPanel.add(new JLabel("Tank"));
        controlPanel.add(Box.createVerticalGlue());

        contentPanel.add(drawPanel);
        contentPanel.add(controlPanel);
        frame.pack();
        frame.setVisible(true);
        frame.setResizable(false);
    }

    /**
     * Builds the main menu bar.
     *
     * @return configured menu bar
     */
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        menuBar.setBackground(new Color(245, 245, 245));
        JMenu fileMenu = new JMenu("File");

        JMenuItem openItem = new JMenuItem("Open");
        openItem.setAccelerator(KeyStroke.getKeyStroke("control O"));
        openItem.addActionListener(e -> openFromFile());

        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.setAccelerator(KeyStroke.getKeyStroke("control S"));
        saveItem.addActionListener(e -> saveToFile());

        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        menuBar.add(fileMenu);

        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        menuBar.add(new JLabel("|"));
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sim.initialize();
                INSTANCE.repaint();
            }
        });
        menuBar.add(resetButton);
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        JButton stepButton = new JButton("Step");
        stepButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean simulationComplete = sim.step();
                INSTANCE.repaint();
                if(simulationComplete) {
                    JOptionPane.showMessageDialog(null, "Simulation complete", "Message", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        menuBar.add(stepButton);
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        JButton fastRunButton = new JButton("FastRun");
        fastRunButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                fastRunButton.setEnabled(false);

                Thread simulationThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final boolean[] keepRepainting = new boolean[]{true};
                        Thread repaintThread = new Thread(new Runnable() {
                            @Override
                            public void run() {
                                while(keepRepainting[0]) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override
                                        public void run() {
                                            INSTANCE.repaint();
                                        }
                                    });

                                    try {
                                        Thread.sleep(33);
                                    } catch(InterruptedException ex) {
                                        break;
                                    }
                                }
                            }
                        }, "PipeSim-RepaintThread");
                        repaintThread.setDaemon(true);
                        repaintThread.start();

                        boolean simulationComplete = false;
                        boolean timedOut = false;
                        long startTimeMs = System.currentTimeMillis();

                        while(!simulationComplete) {
                            simulationComplete = sim.step();
                            if(System.currentTimeMillis() - startTimeMs >= 10000) {
                                timedOut = true;
                                break;
                            }
                        }

                        keepRepainting[0] = false;
                        repaintThread.interrupt();

                        final boolean finalTimedOut = timedOut;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                INSTANCE.repaint();
                                fastRunButton.setEnabled(true);
                                if(finalTimedOut) {
                                    JOptionPane.showMessageDialog(null, "Simulation timed out after 10 seconds.", "Warning", JOptionPane.WARNING_MESSAGE);
                                } else {
                                    JOptionPane.showMessageDialog(null, "Simulation complete", "Message", JOptionPane.INFORMATION_MESSAGE);
                                }
                            }
                        });
                    }
                }, "PipeSim-RunThread");

                simulationThread.start();
            }
        });
        menuBar.add(fastRunButton);
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        menuBar.add(new JLabel("|"));
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        JButton playButton = new JButton("Play");
        JButton stopButton = new JButton("Stop");
        stopButton.setEnabled(false);

        playButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(playThread != null && playThread.isAlive()) {
                    return;
                }

                playStopRequested = false;
                playButton.setEnabled(false);
                stopButton.setEnabled(true);

                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        boolean simulationComplete = false;
                        try {
                            while(!playStopRequested && !simulationComplete) {
                                simulationComplete = sim.step();
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        INSTANCE.repaint();
                                    }
                                });
                                if(simulationComplete || playStopRequested) {
                                    break;
                                }
                                Thread.sleep(100);
                            }
                        } catch(InterruptedException ignored) {
                            // Stopping play interrupts this thread to exit quickly.
                        } finally {
                            final boolean completed = simulationComplete;
                            playThread = null;
                            playStopRequested = false;
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    playButton.setEnabled(true);
                                    stopButton.setEnabled(false);
                                    INSTANCE.repaint();
                                    if(completed) {
                                        JOptionPane.showMessageDialog(null, "Simulation complete", "Message", JOptionPane.INFORMATION_MESSAGE);
                                    }
                                }
                            });
                        }
                    }
                }, "PipeSim-PlayThread");

                playThread = thread;
                thread.start();
            }
        });

        menuBar.add(playButton);
        menuBar.add(Box.createRigidArea(new Dimension(20, 20)));
        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                playStopRequested = true;
                Thread thread = playThread;
                if(thread != null) {
                    thread.interrupt();
                }
            }
        });
        menuBar.add(stopButton);

        return menuBar;
    }

    /**
     * Saves current editor components to a .pipesim file.
     */
    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PipeSim files (*.pipesim)", "pipesim"));
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pipesim")) {
            file = new File(file.getParentFile(), file.getName() + ".pipesim");
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("PIPESIM_V1");
            writer.newLine();
            for (PipeComponent pc : pipeComponents) {
                writer.write(pc.X + "," + pc.Y + "," + pc.type + "," + pc.orientation + "," + pc.tankAmount);
                writer.newLine();
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads components from a .pipesim file into the editor.
     */
    private void openFromFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PipeSim files (*.pipesim)", "pipesim"));
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        ArrayList<PipeComponent> loadedComponents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String header = reader.readLine();
            if (header == null || !"PIPESIM_V1".equals(header.trim())) {
                throw new IOException("Unsupported file format.");
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                String[] parts = trimmed.split(",");
                if (parts.length < 5) {
                    throw new IOException("Malformed line: " + line);
                }

                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                PipeComponent.ComponentType type = PipeComponent.ComponentType.valueOf(parts[2]);
                PipeComponent.Orientation orientation = PipeComponent.Orientation.valueOf(parts[3]);
                int amount = Integer.parseInt(parts[4]);

                PipeComponent component = new PipeComponent(x, y, type, orientation);
                component.tankAmount = component.amountSim = amount;
                // State is intentionally not loaded from files; it is simulation-only.
                loadedComponents.add(component);
            }

            pipeComponents.clear();
            pipeComponents.addAll(loadedComponents);
            addingPipeComponent = null;
            deselectComponents();
            repaint();
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, "Failed to open file: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Creates the editor panel and registers all mouse/keyboard controls.
     */
    public PipeSim() {
        INSTANCE = this;
        sim = new Simulation(pipeComponents);
        setLayout(new GridLayout(1, 1));
        add(Box.createRigidArea(new Dimension(PANEL_SIZE, PANEL_SIZE)));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if(e.getButton() == MouseEvent.BUTTON1) {
                    // Release left click: end selection box
                    if(!movingSelection) {
                        selectionBoxStart = selectionBoxEnd = null;
                    }
                    // Release left click: end moving selection
                    else if(movingSelection) {
                        boolean canMove = true;
                        for(PipeComponent pc : pipeComponents) {
                            if(pc.selected) {
                                PipeComponent componentUnder = getComponentAtPos(pc.tempX, pc.tempY);
                                if(componentUnder != null && !componentUnder.selected) {
                                    canMove = false;
                                    break;
                                }
                            }
                        }
                        if(canMove) { // do not move selection if there is a collision
                            for(PipeComponent pc : pipeComponents) {
                                if(pc.selected) {
                                    pc.X = pc.tempX;
                                    pc.Y = pc.tempY;
                                }
                            }
                        } else {
                            for(PipeComponent pc : pipeComponents) {
                                if(pc.selected) {
                                    pc.tempX = pc.X;
                                    pc.tempY = pc.Y;
                                }
                            }
                        }
                        movingSelection = false;
                    }
                }
                repaint();
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if(e.getButton() == MouseEvent.BUTTON1) {
                    PipeComponent comp = getComponentAtPos(getGridIndex(e.getX()), getGridIndex(e.getY()));
                    // Left click: add component
                    if(addingPipeComponent != null) {
                        deselectComponents();
                        // Replace component under mouse
                        if(getComponentAtPos(addingPipeComponent.X, addingPipeComponent.Y) != null) {
                            deleteComponentAtPos(addingPipeComponent.X, addingPipeComponent.Y);
                        }
                        pipeComponents.add(addingPipeComponent.clone());
                    }
                    // Left click: start selection box
                    else if(comp == null) {
                        selectionBoxStart = e.getPoint();
                        deselectComponents();
                    }
                    // Left click: start moving selection
                    else if(comp.selected) {
                        selectionBoxStart = new Point(getGridIndex(e.getX()), getGridIndex(e.getY()));
                        movingSelection = true;
                    }
                    // Left click: edit component
                    else {
                        comp.editComponent();
                    }
                } else if(e.getButton() == MouseEvent.BUTTON3) {
                    selectionBoxStart = null;
                    deselectComponents();
                    // Right click: deselect pipe component
                    if(addingPipeComponent != null) {
                        addingPipeComponent = null;
                    }
                    // Right click: delete component under mouse
                    else if(getComponentAtPos(getGridIndex(e.getX()), getGridIndex(e.getY())) != null) {
                        deleteComponentAtPos(getGridIndex(e.getX()), getGridIndex(e.getY()));
                    }
                }
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if(movingSelection) {
                    Point currentLoc = new Point(getGridIndex(e.getX()), getGridIndex(e.getY()));
                    if(!currentLoc.equals(selectionBoxStart)) {
                        int dx = currentLoc.x - selectionBoxStart.x;
                        int dy = currentLoc.y - selectionBoxStart.y;
                        for(PipeComponent pc : pipeComponents) {
                            if(pc.selected) {
                                pc.tempX = pc.X + dx;
                                pc.tempY = pc.Y + dy;
                            }
                        }
                    }
                } else if(selectionBoxStart != null) {
                    selectionBoxEnd = e.getPoint();
                    selectComponents();

                }
                repaint();
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                lastMousePos = new Point(e.getX(), e.getY());
                if(addingPipeComponent != null) {
                    addingPipeComponent.X = getGridIndex(e.getX());
                    addingPipeComponent.Y = getGridIndex(e.getY());
                    repaint();
                }
            }
        });

        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        im.put(KeyStroke.getKeyStroke("1"), "key1");
        im.put(KeyStroke.getKeyStroke("2"), "key2");
        im.put(KeyStroke.getKeyStroke("3"), "key3");
        im.put(KeyStroke.getKeyStroke("4"), "key4");
        im.put(KeyStroke.getKeyStroke("5"), "key5");
        im.put(KeyStroke.getKeyStroke("R"), "keyR");
        im.put(KeyStroke.getKeyStroke("DELETE"), "keyDelete");
        am.put("key1", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                addingPipeComponent = new PipeComponent(lastMousePos.x, getGridIndex(lastMousePos.y), PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.NORTH);
                repaint();
            }
        });
        am.put("key2", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                addingPipeComponent = new PipeComponent(getGridIndex(lastMousePos.x), getGridIndex(lastMousePos.y), PipeComponent.ComponentType.CURVED_PIPE, PipeComponent.Orientation.NORTH);
                repaint();
            }
        });
        am.put("key3", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                addingPipeComponent = new PipeComponent(getGridIndex(lastMousePos.x), getGridIndex(lastMousePos.y), PipeComponent.ComponentType.BRIDGE, PipeComponent.Orientation.NORTH);
                repaint();
            }
        });
        am.put("key4", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                addingPipeComponent = new PipeComponent(getGridIndex(lastMousePos.x), getGridIndex(lastMousePos.y), PipeComponent.ComponentType.SPLITTER, PipeComponent.Orientation.NORTH);
                repaint();
            }
        });
        am.put("key5", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                addingPipeComponent = new PipeComponent(getGridIndex(lastMousePos.x), getGridIndex(lastMousePos.y), PipeComponent.ComponentType.CONVERGER, PipeComponent.Orientation.NORTH);
                repaint();
            }
        });
        am.put("keyR", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if(addingPipeComponent != null) {
                    if((e.getModifiers() & ActionEvent.SHIFT_MASK) != 0) { // shift held
                        addingPipeComponent.prevOrientation();
                    } else {
                        addingPipeComponent.nextOrientation();
                    }
                    repaint();
                }
            }
        });
        am.put("keyDelete", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                deleteSelectedComponents();
            }
        });
    }

    /**
     * Sets the currently selected component template for placement.
     *
     * @param comp component template
     */
    public void setSelectedComponent(PipeComponent comp) {
        addingPipeComponent = comp;
    }

    /**
     * Gets the component at a grid position.
     *
     * @param x grid x-coordinate
     * @param y grid y-coordinate
     * @return component at the position, or null
     */
    public PipeComponent getComponentAtPos(int x, int y) {
        for (PipeComponent pc : pipeComponents) {
            if (pc.X == x && pc.Y == y) return pc;
        }
        return null;
    }

    /**
     * Deletes a component at a grid position.
     *
     * @param x grid x-coordinate
     * @param y grid y-coordinate
     */
    public void deleteComponentAtPos(int x, int y) {
        pipeComponents.removeIf(new Predicate<PipeComponent>() {
            @Override
            public boolean test(PipeComponent pipeComponent) {
                return pipeComponent.X == x && pipeComponent.Y == y;
            }
        });
    }

    /**
     * Deletes all selected components.
     */
    public void deleteSelectedComponents() {
        boolean removed = pipeComponents.removeIf(new Predicate<PipeComponent>() {
            @Override
            public boolean test(PipeComponent pipeComponent) {
                return pipeComponent.selected;
            }
        });
        if (removed) {
            selectionBoxStart = null;
            selectionBoxEnd = null;
            movingSelection = false;
            repaint();
        }
    }

    /**
     * Converts panel pixel coordinate to grid index.
     *
     * @param px pixel coordinate
     * @return grid index
     */
    public int getGridIndex(int px) {
        int wx = PANEL_SIZE / GRID_SIZE;
        return (int) (px / wx);
    }

    /**
     * Selects all components currently covered by the selection rectangle.
     */
    public void selectComponents() {
        Point TL = new Point(getGridIndex(Math.min(selectionBoxStart.x, selectionBoxEnd.x)), getGridIndex(Math.min(selectionBoxStart.y, selectionBoxEnd.y)));
        Point BR = new Point(getGridIndex(Math.max(selectionBoxStart.x, selectionBoxEnd.x)), getGridIndex(Math.max(selectionBoxStart.y, selectionBoxEnd.y)));
        for(PipeComponent pc : pipeComponents) {
            pc.selected = pc.X >= TL.x && pc.X <= BR.x && pc.Y >= TL.y && pc.Y <= BR.y;
            if(pc.selected) {
                pc.tempX = pc.X;
                pc.tempY = pc.Y;
            }
        }
    }

    /**
     * Clears selection state for all components.
     */
    public void deselectComponents() {
        for(PipeComponent pc : pipeComponents) {
            pc.selected = false;
        }
    }

    /**
     * Paints the full editor viewport and overlays.
     *
     * @param g graphics context
     */
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g.create();

        g.setColor(BG_COLOR);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.lightGray);
        for(int i = 0; i <= GRID_SIZE; ++i) {
            int dx = i * (PANEL_SIZE - 1) / GRID_SIZE;
            g.drawLine(0, dx, getWidth(), dx);
            g.drawLine(dx, 0, dx, getHeight());
        }

        for(PipeComponent c : pipeComponents) {
            c.draw(g2);
        }

        if(addingPipeComponent != null) {
            g.setColor(BG_COLOR);
            int offX = addingPipeComponent.X * (PANEL_SIZE - 1) / GRID_SIZE;
            int offY = addingPipeComponent.Y * (PANEL_SIZE - 1) / GRID_SIZE;
            int wid = (addingPipeComponent.X + 1) * (PANEL_SIZE - 1) / GRID_SIZE - offX;
            int hei = (addingPipeComponent.Y + 1) * (PANEL_SIZE - 1) / GRID_SIZE - offY;
            int radius = wid * 2;
            float[] dist = {0f, 1f};
            Color[] colors = {
                    new Color(255, 255, 255, 200),   // fully opaque center
                    new Color(255, 255, 255, 0)      // fully transparent edge
            };
            RadialGradientPaint paint = new RadialGradientPaint(
                    offX + wid / 2.0f, offY + hei / 2.0f,          // center
                    radius,
                    dist,
                    colors
            );
            g2.setPaint(paint);
            g2.fillOval(offX + wid / 2 - radius, offY + hei / 2 - radius, radius * 2, radius * 2);
            g.fillRect(offX, offY, wid, hei);
            addingPipeComponent.draw(g2);
        }

        if(selectionBoxEnd != null) {
            float dot = 4f; // length of each dot
            float gap = 6f; // space between dots
            g2.setStroke(new BasicStroke(
                    2f,               // line thickness
                    BasicStroke.CAP_ROUND,  // round ends = round dots
                    BasicStroke.JOIN_ROUND,
                    0f,
                    new float[]{dot, gap},  // dash pattern
                    0f
            ));
            int wid = selectionBoxEnd.x - selectionBoxStart.x;
            int hei = selectionBoxEnd.y - selectionBoxStart.y;
            g2.drawRect(
                    wid > 0 ? selectionBoxStart.x : selectionBoxEnd.x,
                    hei > 0 ? selectionBoxStart.y : selectionBoxEnd.y,
                    Math.abs(wid), Math.abs(hei)
            );
        }
    }
}