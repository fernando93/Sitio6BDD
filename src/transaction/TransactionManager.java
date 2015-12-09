/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package transaction;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import modelo.dto.DataTable;
import modelo.util.ConnectionManager;
import remote.Sitio;
import remote.util.InterfaceManager;
import remote.util.InterfaceManager.Interfaces;
import remote.util.QueryManager;

/**
 *
 * @author jdosornio
 */
public class TransactionManager {

    private static final String COLUMNA_ID_EMPLEADO = "numero";
    private static final String[] FRAG_LLAVES = {COLUMNA_ID_EMPLEADO, "correo", "adscripcion_id",
        "departamento_id", "plantel_id", "direccion_id"};
    private static final String[] FRAG_DATOS = {COLUMNA_ID_EMPLEADO, "primer_nombre", "segundo_nombre",
        "apellido_paterno", "apellido_materno", "puesto_id"};
    private static final String COLUMNA_ID_PLANTEL = "id";
    private static final String EMPLEADO = "empleado";
    private static final String PLANTEL = "plantel";
    private static final short BIEN = 1;
    private static final int LLAVES = BIEN;
    private static final short MAL = 0;
    private static final int NOMBRES = MAL;

    public static boolean insertReplicado(boolean savePKs, String tabla,
            DataTable datos) {
        boolean ok = true;

        System.out.println("---------Start Global transaction----------");
        try {
            short result = QueryManager.broadInsert(savePKs, tabla, datos);

            if (result == MAL) {
                ok = false;
                rollback();
            } else {
                commit();
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
            ok = false;
        }

        System.out.println("---------End Global transaction----------");
        return ok;
    }

    public static boolean insertEmpleado(DataTable datos) {
        boolean ok = true;

        System.out.println("---------Start Insert Empleado transaction---------- ");

        if (!existeEmpleado(datos)) {

            short result = MAL;
            DataTable[] fragmentos;
            datos.rewind();
            datos.next();
            List<Interfaces> sitios = new ArrayList<>();
            fragmentos = datos.fragmentarVertical(FRAG_DATOS, FRAG_LLAVES);
            datos.rewind();
            datos.next();
            if (datos.getInt("adscripcion_id") != 2) {
                //Insert en sitio 1 y 2

                result = QueryManager.uniInsert(false, Interfaces.SITIO_1, EMPLEADO,
                        fragmentos[NOMBRES]) != null ? BIEN : MAL;
                System.out.println("Sitio 1: " + result);
                result *= QueryManager.uniInsert(false, Interfaces.SITIO_2, EMPLEADO,
                        fragmentos[LLAVES]) != null ? BIEN : MAL;
                System.out.println("Sitio 2: " + result);

                sitios.add(Interfaces.SITIO_1);
                sitios.add(Interfaces.SITIO_2);
            } else {

                Map<String, Object> condicion = new HashMap<>();
                condicion.put(COLUMNA_ID_PLANTEL, datos.getInt("plantel_id"));

                DataTable plantel = QueryManager.uniGet(Interfaces.LOCALHOST,
                        PLANTEL, null, null, condicion);

                //se verifica en su nodo si se encuentra el plantel al que se insertara
                // cambiar por sus nodos el nombre de la variable de sitio y la interface
                if (plantel != null && plantel.getRowCount() != 0) {
                    //este es su nodo ya no lo inserten de nuevo
                    result = QueryManager.localInsert(false, EMPLEADO, fragmentos[LLAVES])
                            != null ? BIEN : MAL;

                    System.out.println("Sitio Local: " + result);

                    result *= QueryManager.uniInsert(false, Interfaces.SITIO_5,
                            EMPLEADO, fragmentos[LLAVES]) != null ? BIEN : MAL;
                    System.out.println("Sitio 5: " + result);
                    
                    result *= QueryManager.uniInsert(false, Interfaces.SITIO_7,
                            EMPLEADO, fragmentos[NOMBRES]) != null ? BIEN : MAL;
                    System.out.println("Sitio 7: " + result);

                    sitios.add(Interfaces.LOCALHOST);
                    sitios.add(Interfaces.SITIO_5);
                    sitios.add(Interfaces.SITIO_7);

                } else {
//                    revisar en los demas nodos
//                     tienen que verificar en los demas nodos en un solo sitio si se encuentra el plantel
//                     aqui se verifica la zona 1
//                    busca en la zona 1 si se encuentra el platel

                    plantel = QueryManager.uniGet(Interfaces.SITIO_2, PLANTEL,
                            null, null, condicion);

                    if (plantel != null && plantel.getRowCount() != 0) {
                        //aqui se encuentra

                        result = QueryManager.uniInsert(false, Interfaces.SITIO_1, EMPLEADO,
                                fragmentos[NOMBRES]) != null ? BIEN : MAL;
                        System.out.println("Sitio 1: " + result);

                        result *= QueryManager.uniInsert(false, Interfaces.SITIO_2, EMPLEADO,
                                fragmentos[LLAVES]) != null ? BIEN : MAL;

                        System.out.println("Sitio 2: " + result);

                        sitios.add(Interfaces.SITIO_1);
                        sitios.add(Interfaces.SITIO_2);

                    } else {
//                        aqui se veririca la zona 3

                        plantel = QueryManager.uniGet(Interfaces.SITIO_4, PLANTEL,
                                null, null, condicion);

                        if (plantel != null && plantel.getRowCount() != 0) {

                            result = QueryManager.uniInsert(false, Interfaces.SITIO_3, EMPLEADO,
                                    fragmentos[LLAVES]) != null ? BIEN : MAL;
                            System.out.println("Sitio 3: " + result);

                            result *= QueryManager.uniInsert(false, Interfaces.SITIO_4, EMPLEADO,
                                    fragmentos[NOMBRES]) != null ? BIEN : MAL;
                            System.out.println("Sitio 4: " + result);

                            sitios.add(Interfaces.SITIO_5);
                            sitios.add(Interfaces.SITIO_6);
                            sitios.add(Interfaces.SITIO_7);

                        }
                    }
                }
            }
            if (result == BIEN) {
                commit(sitios);
            } else {
                ok = false;
                rollback(sitios);
            }
        } else {
            ok = false;
            System.out.println("Empleado id existe");
        }
        System.out.println("Insert empleado: " + ok);
        System.out.println("---------End Insert Empleado transaction----------");
        return ok;
    }

    /**
     * Retorna verdadero si existe el id del empleado, falso de otra forma.
     *
     * @param datos
     * @return
     */
    public static boolean existeEmpleado(DataTable datos) {
        boolean ok;
        Map<String, Object> condicion = new HashMap<>();
        datos.rewind();
        datos.next();
        condicion.put(COLUMNA_ID_EMPLEADO, datos.getString(COLUMNA_ID_EMPLEADO));

        try {
            ok = QueryManager.uniGet(Interfaces.SITIO_3, EMPLEADO, null, null, condicion)
                    .next();
            if (!ok) {
                ok = QueryManager.uniGet(Interfaces.SITIO_7, EMPLEADO, null, null, condicion)
                        .next();
                if (!ok) {
                    ok = QueryManager.uniGet(Interfaces.SITIO_1, EMPLEADO, null, null, condicion)
                            .next();
                }
            }

        } catch (NullPointerException e) {
            System.out.println("NullPointer uniGet verificarExistenciaEmpleado");
            ok = true;
        }
        return ok;
    }

    public static boolean insertPlantel(DataTable datos) {
        boolean ok = true;

        System.out.println("---------Start Plantel transaction---------- ");

        short result = MAL;
        datos.rewind();
        datos.next();
        List<Interfaces> sitios = new ArrayList<>();

        Integer siguienteID = obtenerSiguienteID(PLANTEL, COLUMNA_ID_PLANTEL, Interfaces.SITIO_1,
                Interfaces.SITIO_3, Interfaces.SITIO_7);

        if (siguienteID > 0) {
            datos.rewind();
            datos.next();
            datos.setObject(COLUMNA_ID_PLANTEL, siguienteID);

            if (datos.getInt("zona_id") == 1) {

                System.out.println("Zona 1");
                result = QueryManager.uniInsert(false, Interfaces.SITIO_2, PLANTEL, datos)
                        != null ? BIEN : MAL;
                result *= QueryManager.uniInsert(false, Interfaces.SITIO_1, PLANTEL, datos)
                        != null ? BIEN : MAL;

                sitios.add(Interfaces.SITIO_1);
                sitios.add(Interfaces.SITIO_2);

            } else if (datos.getInt("zona_id") == 2) {

                System.out.println("Zona 2");
                result = QueryManager.uniInsert(false, Interfaces.SITIO_3, PLANTEL, datos)
                        != null ? BIEN : MAL;
                result *= QueryManager.uniInsert(false, Interfaces.SITIO_4, PLANTEL, datos)
                        != null ? BIEN : MAL;

                sitios.add(Interfaces.SITIO_3);
                sitios.add(Interfaces.SITIO_4);

            } else if (datos.getInt("zona_id") == 3) {

                System.out.println("Zona 3");
                result = QueryManager.uniInsert(false, Interfaces.SITIO_5, PLANTEL, datos)
                        != null ? BIEN : MAL;
                result *= QueryManager.localInsert(false, PLANTEL, datos)
                        != null ? BIEN : MAL;
                result *= QueryManager.uniInsert(false, Interfaces.SITIO_7, PLANTEL, datos)
                        != null ? BIEN : MAL;

                sitios.add(Interfaces.SITIO_5);
                sitios.add(Interfaces.LOCALHOST);
                sitios.add(Interfaces.SITIO_7);
            }

            if (result == MAL) {
                ok = false;
                rollback(sitios);
            } else {
                commit(sitios);
            }
        }
        System.out.println("insert plantel: " + ok);
        System.out.println("---------End Plantel transaction----------");
        return ok;
    }

    private static int obtenerSiguienteID(String tabla, String columnaID,
            Interfaces... interfacesSitios) {

        int mayor = -1;
        int idSitio;
        for (Interfaces interfaceSitio : interfacesSitios) {
            idSitio = QueryManager.getMaxId(interfaceSitio, tabla, columnaID);
            if (idSitio > mayor) {
                mayor = idSitio;
            }
        }

        return ++mayor;
    }

    public static boolean updateReplicado(String tabla, DataTable datos,
            Map<String, ?> attrWhere) {

        boolean ok = true;

        System.out.println("---------Start Global transaction----------");
        try {
            short result = QueryManager.broadUpdate(tabla, datos, attrWhere);

            if (result == 0) {
                ok = false;
                rollback();
            } else {
                commit();
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
            ok = false;
        }

        System.out.println("---------End Global transaction----------");
        return ok;
    }

    public static boolean updateEmpleado(DataTable datos, Map<String, ?> attrWhere) {
        System.out.println("---------Start Update Empleado transaction---------- ");
        boolean ok = true;

        short result = MAL;
        DataTable[] fragmentos;
        datos.rewind();
        datos.next();
        List<Interfaces> sitios = new ArrayList<>();
        fragmentos = datos.fragmentarVertical(FRAG_DATOS, FRAG_LLAVES);
        datos.rewind();
        datos.next();
        if (datos.getInt("adscripcion_id") != 2) {
            //Zona1
            result = QueryManager.uniUpdate(Interfaces.SITIO_1, EMPLEADO,
                    fragmentos[NOMBRES], attrWhere) == true ? BIEN : MAL;
            System.out.println("Sitio 1: " + result);
            result *= QueryManager.uniUpdate(Interfaces.SITIO_2, EMPLEADO,
                    fragmentos[LLAVES], attrWhere) == true ? BIEN : MAL;
            System.out.println("Sitio 2: " + result);

            sitios.add(Interfaces.SITIO_1);
            sitios.add(Interfaces.SITIO_2);
        } else {
            //Zona local
            Map<String, Object> condicion = new HashMap<>();
            condicion.put(COLUMNA_ID_PLANTEL, datos.getInt("plantel_id"));

            DataTable plantel = QueryManager.uniGet(Interfaces.SITIO_3,
                    PLANTEL, null, null, condicion);

            if (plantel != null && plantel.getRowCount() != 0) {
                result *= QueryManager.uniUpdate(Interfaces.SITIO_3, EMPLEADO,
                        fragmentos[NOMBRES], attrWhere) == true ? BIEN : MAL;
                
                System.out.println("Sitio 3: " + result);

                result *= QueryManager.uniUpdate(Interfaces.SITIO_4, EMPLEADO,
                        fragmentos[NOMBRES], attrWhere) == true ? BIEN : MAL;
                System.out.println("Sitio 4: " + result);

                sitios.add(Interfaces.SITIO_3);
                sitios.add(Interfaces.SITIO_4);

            } else {
                //Zona 1
                plantel = QueryManager.uniGet(Interfaces.SITIO_2, PLANTEL,
                        null, null, condicion);

                if (plantel != null && plantel.getRowCount() != 0) {

                    result = QueryManager.uniUpdate(Interfaces.SITIO_1, EMPLEADO,
                            fragmentos[NOMBRES], attrWhere) == true ? BIEN : MAL;
                    System.out.println("Sitio 1: " + result);

                    result *= QueryManager.uniUpdate(Interfaces.SITIO_2, EMPLEADO,
                            fragmentos[LLAVES], attrWhere) == true ? BIEN : MAL;

                    System.out.println("Sitio 2: " + result);

                    sitios.add(Interfaces.SITIO_1);
                    sitios.add(Interfaces.SITIO_2);

                } else {
                    //Zona 3
                    plantel = QueryManager.uniGet(Interfaces.SITIO_7, PLANTEL,
                            null, null, condicion);

                    if (plantel != null && plantel.getRowCount() != 0) {

                        
                        result = QueryManager.uniUpdate(Interfaces.SITIO_5, EMPLEADO,
                                fragmentos[LLAVES], attrWhere) == true ? BIEN : MAL;
                        System.out.println("Sitio 5: " + result);

                        result = QueryManager.localUpdate(EMPLEADO, fragmentos[LLAVES],
                        attrWhere) == true ? BIEN : MAL;
                        System.out.println("Sitio LocalHost: " + result);

                        result *= QueryManager.uniUpdate(Interfaces.SITIO_7, EMPLEADO,
                                fragmentos[NOMBRES], attrWhere) == true ? BIEN : MAL;
                        System.out.println("Sitio 7: " + result);

                        sitios.add(Interfaces.SITIO_5);
                        sitios.add(Interfaces.LOCALHOST);
                        sitios.add(Interfaces.SITIO_7);

                    }
                }
            }
        }
        if (result == BIEN) {
            commit(sitios);
        } else {
            ok = false;
            rollback(sitios);
        }
        System.out.println("--------- Update Empleado: " + ok);
        System.out.println("---------End Update Empleado transaction---------- ");
        return ok;
    }

    public static boolean deleteReplicado(String tabla, Map<String, ?> attrWhere) {
        boolean ok = true;

        System.out.println("---------Start Global transaction----------");
        try {
            short result = QueryManager.broadDelete(tabla, attrWhere);

            if (result == 0) {
                ok = false;
                rollback();
            } else {
                commit();
            }

        } catch (InterruptedException ex) {
            Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
            ok = false;
        }

        System.out.println("---------End Global transaction----------");
        return ok;
    }

    public static boolean deleteEmpleado(Map<String, ?> attrWhere) {
        System.out.println("---------Start Delete Empleado transaction---------- ");
        boolean ok = true;

        short result = MAL;
        List<Interfaces> sitios = new ArrayList<>();

        //Zona local
        Map<String, Object> condicion = new HashMap<>();
        condicion.put(COLUMNA_ID_EMPLEADO, attrWhere.get(COLUMNA_ID_EMPLEADO));

        DataTable empleado = QueryManager.uniGet(Interfaces.LOCALHOST,
                EMPLEADO, null, null, condicion);

        if (empleado != null && empleado.getRowCount() != 0) {
            result = QueryManager.localDelete(EMPLEADO, attrWhere)
                    == true ? BIEN : MAL;

            System.out.println("Sitio Local: " + result);

            result *= QueryManager.uniDelete(Interfaces.SITIO_5, EMPLEADO, attrWhere)
                    == true ? BIEN : MAL;
            System.out.println("Sitio 5: " + result);
            
            result *= QueryManager.uniDelete(Interfaces.SITIO_7, EMPLEADO, attrWhere)
                    == true ? BIEN : MAL;
            System.out.println("Sitio 7: " + result);

            sitios.add(Interfaces.LOCALHOST);
            sitios.add(Interfaces.SITIO_5);
            sitios.add(Interfaces.SITIO_7);

        } else {
            //Zona 1
            empleado = QueryManager.uniGet(Interfaces.SITIO_2, EMPLEADO,
                    null, null, condicion);

            if (empleado != null && empleado.getRowCount() != 0) {

                result = QueryManager.uniDelete(Interfaces.SITIO_1, EMPLEADO,
                        attrWhere) == true ? BIEN : MAL;
                System.out.println("Sitio 1: " + result);

                result *= QueryManager.uniDelete(Interfaces.SITIO_2, EMPLEADO,
                        attrWhere) == true ? BIEN : MAL;

                System.out.println("Sitio 2: " + result);

                sitios.add(Interfaces.SITIO_1);
                sitios.add(Interfaces.SITIO_2);

            } else {
                //Zona 3
                empleado = QueryManager.uniGet(Interfaces.SITIO_4, EMPLEADO,
                        null, null, condicion);

                if (empleado != null && empleado.getRowCount() != 0) {

                    result = QueryManager.uniDelete(Interfaces.SITIO_3, EMPLEADO,
                            attrWhere) == true ? BIEN : MAL;
                    System.out.println("Sitio 3: " + result);

                    result *= QueryManager.uniDelete(Interfaces.SITIO_4, EMPLEADO,
                            attrWhere) == true ? BIEN : MAL;
                    System.out.println("Sitio 4: " + result);
                    
                    sitios.add(Interfaces.SITIO_3);
                    sitios.add(Interfaces.SITIO_4);
                }
            }
        }

        if (result == BIEN) {
            commit(sitios);
        } else {
            ok = false;
            rollback(sitios);
        }
        System.out.println("--------- Delete Empleado: " + ok);
        System.out.println("---------End Delete Empleado transaction---------- ");
        return ok;
    }

    public static DataTable consultarEmpleados() {
        String[] columnas = {
            "numero",
            "primer_nombre",
            "segundo_nombre",
            "apellido_paterno",
            "apellido_materno",};

        DataTable fragDatosSitio1 = QueryManager.uniGet(Interfaces.SITIO_1, "empleado", columnas, null, null);
        DataTable fragDatosSitio4 = QueryManager.uniGet(Interfaces.SITIO_4, "empleado", columnas, null, null);
        DataTable fragDatosSitio7 = QueryManager.uniGet(Interfaces.SITIO_7, "empleado", columnas, null, null);
        
        return DataTable.combinarFragH(fragDatosSitio1, fragDatosSitio4, fragDatosSitio7);
    }
    
    public static DataTable getEmpleado(String[] columnas, Map<String, ?> condicion){
        System.out.println("---------Start GetEmpleado transaction---------- ");

        
        String[] fragLlaves = {"numero", "correo", "adscripcion_id",
            "departamento_id", "plantel_id", "direccion_id"};
        List<String> listaLlaves = Arrays.asList(fragLlaves);
        List<String> listaColumnas = null;
        if(columnas != null){
            listaColumnas = Arrays.asList(columnas);
        }
        
        //Se busca en el nodo local al Empleado[NOMBRES]
        DataTable empleado = QueryManager.uniGet(Interfaces.LOCALHOST,
                EMPLEADO, columnas, null, condicion);
        if(columnas == null || listaLlaves.retainAll(listaColumnas)){
            DataTable llaves = QueryManager.uniGet(Interfaces.SITIO_2, EMPLEADO,
                columnas, null, condicion);
           empleado = DataTable.combinarFragV(empleado, llaves, "numero");
        }
        
        if (empleado == null && empleado.getRowCount() == 0) {
            //En caso de no encontrarse se busca en el nodo 4
            empleado = QueryManager.uniGet(Interfaces.SITIO_4, EMPLEADO, null,
                    null, condicion);
            if(columnas == null || listaLlaves.retainAll(listaColumnas)){
                DataTable llaves = QueryManager.uniGet(Interfaces.SITIO_3, EMPLEADO,
                    columnas, null, condicion);
               empleado = DataTable.combinarFragV(empleado, llaves, "numero");
            }
            
            if (empleado == null && empleado.getRowCount() == 0) {
                //Por ultimo se busca en el sitio 7 en caso de no encontrarse
                empleado = QueryManager.uniGet(Interfaces.SITIO_7, EMPLEADO, null,
                        null, condicion);
                if(columnas == null || listaLlaves.retainAll(listaColumnas)){
                    DataTable llaves = QueryManager.uniGet(Interfaces.SITIO_5, EMPLEADO,
                        columnas, null, condicion);
                   empleado = DataTable.combinarFragV(empleado, llaves, "numero");
                }
            }
        }

        System.out.println("---------End GetEmpleado transaction----------");
        return empleado;
    }
    
    public static DataTable consultarPlanteles(Map attrWhere) {
        
        //Zona 1
        DataTable fragDatosZona1 = QueryManager.uniGet(
                Interfaces.SITIO_1, PLANTEL, null, null, attrWhere);
        //Zona 2
        DataTable fragDatosZona2 = QueryManager.uniGet(
                Interfaces.SITIO_3, PLANTEL, null, null, attrWhere);
        //Zona 3
        DataTable fragDatosZona3 = QueryManager.uniGet(
                Interfaces.LOCALHOST,PLANTEL, null, null, attrWhere);
        
        return DataTable.combinarFragH(fragDatosZona1, fragDatosZona2,
                fragDatosZona3);
    }
    
    public static DataTable getPlantel(Map<String, ?> condicion){
        System.out.println("---------Start GetPlantel transaction---------- ");

        //Se busca en el nodo local (Zona 3)
        DataTable plantel = QueryManager.uniGet(Interfaces.LOCALHOST,
                PLANTEL, null, null, condicion);
        
        if (plantel == null || plantel.isEmpty()) {
            //Si no se encontró en la Zona 3 buscar en la Zona 2
            plantel = QueryManager.uniGet(Interfaces.SITIO_3, PLANTEL, null, null,
                    condicion);
            
            if (plantel == null || plantel.isEmpty()) {
                //Si no esta en la Zona 2 buscar en la Zona 1
                plantel = QueryManager.uniGet(Interfaces.SITIO_1, PLANTEL,
                        null, null, condicion);
                
                //Si no se encontró aquí regresar el plantel vacío de todos modos
            }
        }

        System.out.println("---------End GetEmpleado transaction----------");
        return plantel;
    }

    public static void commit() throws InterruptedException {
        List<Thread> hilosInsert = new ArrayList<>();

        //Commit local
        ConnectionManager.commit();
        ConnectionManager.cerrar();

        //Obtener todas las interfaces de sitio
        for (InterfaceManager.Interfaces interfaceSitio : InterfaceManager.getInterfacesRegistradas()) {

            if (interfaceSitio.equals(Interfaces.LOCALHOST)) {
                continue;
            }

            Runnable hacerCommit = new Runnable() {
                @Override
                public void run() {
                    try {
                        Sitio sitio = InterfaceManager.getInterface(
                                InterfaceManager.getInterfaceServicio(interfaceSitio));

                        if (sitio != null) {
                            boolean ok = sitio.commit();

                            System.out.println("Thread de commit a la interface: "
                                    + interfaceSitio + ", resultado = " + ok);
                        }
                    } catch (RemoteException | NotBoundException ex) {
                        Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };

            Thread hilo = new Thread(hacerCommit);
            hilo.start();
            hilosInsert.add(hilo);
        }

        for (Thread hilo : hilosInsert) {
            hilo.join();
        }

        System.out.println("fin de commit global");
    }

    public static void rollback() throws InterruptedException {
        List<Thread> hilosInsert = new ArrayList<>();

        //Rollback local
        ConnectionManager.rollback();
        ConnectionManager.cerrar();

        //Obtener todas las interfaces de sitio
        for (InterfaceManager.Interfaces interfaceSitio : InterfaceManager.getInterfacesRegistradas()) {

            if (interfaceSitio.equals(Interfaces.LOCALHOST)) {
                continue;
            }

            Runnable hacerRollback = new Runnable() {
                @Override
                public void run() {
                    try {
                        Sitio sitio = InterfaceManager.getInterface(
                                InterfaceManager.getInterfaceServicio(interfaceSitio));

                        if (sitio != null) {
                            boolean ok = sitio.rollback();

                            System.out.println("Thread de rollback a la interface: "
                                    + interfaceSitio + ", resultado = " + ok);
                        }
                    } catch (RemoteException | NotBoundException ex) {
                        Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            };

            Thread hilo = new Thread(hacerRollback);
            hilo.start();
            hilosInsert.add(hilo);
        }

        for (Thread hilo : hilosInsert) {
            hilo.join();
        }

        System.out.println("fin de rollback global");
    }

    private static void commit(List<Interfaces> interfaces) {

        for (Interfaces interfaceSitio : interfaces) {
            if (interfaceSitio == Interfaces.LOCALHOST) {
                ConnectionManager.commit();
                ConnectionManager.cerrar();
            } else {
                try {
                    Sitio sitio = InterfaceManager.getInterface(
                            InterfaceManager.getInterfaceServicio(interfaceSitio));
                    if (sitio != null) {
                        boolean ok = sitio.commit();

                        System.out.println("Thread de commit a la interface: "
                                + interfaceSitio + ", resultado = " + ok);
                    }
                } catch (RemoteException | NotBoundException ex) {
                    Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private static void rollback(List<Interfaces> interfaces) {
        for (Interfaces interfaceSitio : interfaces) {
            if (interfaceSitio == Interfaces.LOCALHOST) {
                ConnectionManager.rollback();
                ConnectionManager.cerrar();
            } else {
                try {
                    Sitio sitio = InterfaceManager.getInterface(
                            InterfaceManager.getInterfaceServicio(interfaceSitio));
                    if (sitio != null) {
                        boolean ok = sitio.rollback();

                        System.out.println("Thread de commit a la interface: "
                                + interfaceSitio + ", resultado = " + ok);
                    }
                } catch (RemoteException | NotBoundException ex) {
                    Logger.getLogger(TransactionManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }

}
