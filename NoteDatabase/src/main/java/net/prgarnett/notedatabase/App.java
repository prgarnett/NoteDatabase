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
    
    public App()
    {
        String database = "notesdatabase";
        String password = "b.cjom7OnBzTRF.zsYbhJxHC5DYdRO1";
        
        driver = GraphDatabase.driver( "bolt://sb11.stations.graphenedb.com:24786", AuthTokens.basic( database, password ));
        session = driver.session();
        
        registerShutdownHook(session, driver);
    }
    
    public void startApp()
    {
        ControlPanel control = new ControlPanel(this.getDriver());
        control.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        control.setVisible(true);
    }
    
    
    public static void main(String args[])
    {
        App noteDatabase = new App();
        noteDatabase.startApp();
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
