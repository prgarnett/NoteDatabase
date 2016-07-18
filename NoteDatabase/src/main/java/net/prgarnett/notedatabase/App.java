/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.prgarnett.notedatabase;

import java.awt.Container;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;

/**
 *
 * @author philipgarnett
 */
public class App 
{
    private final Driver driver;
    private final Session session;   
    
    App(String pathname)
    {
        String database = "";
        String password = "";
        
        try
        {
            File auth = new File(pathname);
            Scanner lineScan = new Scanner(auth);
            database = lineScan.nextLine();
            password = lineScan.nextLine();
        }
        catch (FileNotFoundException e)
        {
            System.err.println("Auth File not present: " + e.getMessage());
        }
        
        driver = GraphDatabase.driver( "bolt://sb11.stations.graphenedb.com:24786", AuthTokens.basic( database, password ));
        session = driver.session();
        
        registerShutdownHook(session, driver);
    }
    
    public static void main(String args[])
    {
        App noteDatabase = new App(args[0]);
        
        GraphDatabaseForm graphForm = new GraphDatabaseForm(noteDatabase.getDriver(), noteDatabase.getSession());
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(GraphDatabaseForm.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        
        JFrame frame = new JFrame("ScrollDemo2");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        JComponent newContentPane = new NewNotePanel();
        newContentPane.setOpaque(true); //content panes must be opaque
        
        JScrollPane scroller = new JScrollPane(newContentPane);
        scroller.setPreferredSize(new Dimension(200,200));
        
        frame.setContentPane(scroller);
 
        //Display the window.
        frame.pack();
        frame.setVisible(true);
       
        
//        JPanel panel = new JPanel();
//        Container c = graphForm.getContentPane();
//
//        JScrollPane jsp = new JScrollPane(panel);
//        c.add(jsp);
        
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            graphForm.setVisible(true);
        });
    }
    
    private static void registerShutdownHook( final Session session, final Driver driver )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running example before it's completed)
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                session.close();
                driver.close();
            }
        } );
    }

    /**
     * @return the driver
     */
    public Driver getDriver() {
        return driver;
    }

    /**
     * @return the session
     */
    public Session getSession() {
        return session;
    }
}
