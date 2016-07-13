/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.prgarnett.notedatabase;

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
    
    App()
    {
        driver = GraphDatabase.driver( "bolt://sb11.stations.graphenedb.com:24786", AuthTokens.basic( "NotesDatabase", "jQxCrLUwRh3fkFGG9Rol" ));
        session = driver.session();
        
        registerShutdownHook(session, driver);
    }
    
    public static void main(String args[])
    {
        App noteDatabase = new App();
        
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

        JScrollPane scrPane = new JScrollPane();
        
        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> {
            graphForm.add(scrPane);
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
