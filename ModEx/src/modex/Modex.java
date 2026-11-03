package modex;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * Implementa las tres estrategias para resolver ModEx.
 *
 * Moderar al agente i elimina su aporte o_i^2 * inf_i del extremismo y cuesta
 * costo[i]. Por eso, elegir agentes equivale a una mochila 0/1: maximizar el
 * aporte eliminado sin superar Rmax.
 */
public final class Modex {
    private Modex() {}

    /** Fuerza bruta examina 2^n estrategias; se limita para evitar tiempos enormes. */
    public static final int MAX_FB = 30;

    private static final long RESERVA_HEAP_PD = 32L * 1024 * 1024;

    // ---------------------------------------------------------------------
    // Fuerza bruta

    /** Prueba cada combinación de agentes moderados y conserva la mejor aplicable. */
    public static Solucion modexFB(RedSocial red) {
        if (red.n > MAX_FB) {
            throw new IllegalArgumentException(
                    "Fuerza bruta limitada a n <= " + MAX_FB + " (n actual = " + red.n + ").");
        }

        long inicio = System.nanoTime();
        FuerzaBrutaEstado estado = new FuerzaBrutaEstado(red);
        buscarFuerzaBruta(red, 0, 0, 0, estado);
        int[] agentesModerados = estado.mejorEstrategia;

        return Solucion.crear("Fuerza bruta", red, agentesModerados,
                System.nanoTime() - inicio);
    }

    /** Explora las decisiones de moderar/no moderar y poda ramas que exceden Rmax. */
    private static void buscarFuerzaBruta(RedSocial red, int agente, long esfuerzo,
            double aporte, FuerzaBrutaEstado estado) {
        if (agente == red.n) {
            if (esMejor(aporte, esfuerzo, estado.mejorAporte, estado.mejorEsfuerzo)) {
                estado.mejorAporte = aporte;
                estado.mejorEsfuerzo = esfuerzo;
                estado.mejorEstrategia = estado.actual.clone();
            }
            return;
        }

        estado.actual[agente] = 0;
        buscarFuerzaBruta(red, agente + 1, esfuerzo, aporte, estado);

        long nuevoEsfuerzo = esfuerzo + red.costo[agente];
        if (nuevoEsfuerzo <= red.rmax) {
            estado.actual[agente] = 1;
            buscarFuerzaBruta(red, agente + 1, nuevoEsfuerzo,
                    aporte + red.aporte[agente], estado);
        }
        estado.actual[agente] = 0;
    }

    private static final class FuerzaBrutaEstado {
        final int[] actual;
        int[] mejorEstrategia;
        double mejorAporte = -1;
        long mejorEsfuerzo = Long.MAX_VALUE;

        FuerzaBrutaEstado(RedSocial red) {
            actual = new int[red.n];
            mejorEstrategia = new int[red.n];
        }
    }

    /** Compara soluciones: más aporte eliminado y, en empate, menos esfuerzo. */
    private static boolean esMejor(double aporte, long esfuerzo,
            double mejorAporte, long mejorEsfuerzo) {
        double tolerancia = 4.0 * Math.max(Math.ulp(aporte), Math.ulp(mejorAporte));
        if (aporte > mejorAporte + tolerancia) {
            return true;
        }
        return Math.abs(aporte - mejorAporte) <= tolerancia
                && esfuerzo < mejorEsfuerzo;
    }

    // ---------------------------------------------------------------------
    // Algoritmo voraz

    /**
     * Ordena los agentes por aporte eliminado por unidad de esfuerzo. Luego
     * toma los que quepan y compara el resultado con moderar solo al mejor agente.
     */
    public static Solucion modexV(RedSocial red) {
        long inicio = System.nanoTime();
        int[] agentesModerados = modexVVariante(red, 1);

        return Solucion.crear("Voraz", red, agentesModerados,
                System.nanoTime() - inicio);
    }

    /** Variante 0=razón, 1=dos órdenes, 2=dos órdenes + intercambio, 3=lookahead. */
    static int[] modexVVariante(RedSocial red, int variante) {
        Integer[] porRazon = crearCandidatos(red);
        Arrays.sort(porRazon, (a,b) -> compararCandidatos(red,a,b));
        int[] mejor = llenarMochilaVoraz(red, porRazon, 0);
        if (variante == 0) return mejor;

        Integer[] porAporte = porRazon.clone();
        Arrays.sort(porAporte, (a,b) -> {
            int orden = Double.compare(red.aporte[b],red.aporte[a]);
            if (orden != 0) return orden;
            orden = Integer.compare(red.costo[a],red.costo[b]);
            return orden != 0 ? orden : Integer.compare(a,b);
        });
        mejor = mejorEstrategia(red, mejor, llenarMochilaVoraz(red, porAporte, 0));
        if (variante == 1) return mejor;
        if (variante == 2) return mejorEstrategia(red, mejor, mejorarConIntercambio(red, mejor));
        if (variante >= 3) return mejorEstrategia(red, mejor, enumerarPrefijo(red, porRazon, 6));
        return mejor;
    }

    private static int[] llenarMochilaVoraz(RedSocial red, Integer[] orden, int inicio) {
        int[] seleccion = new int[red.n];
        long esfuerzo = 0;
        for (int pos = inicio; pos < orden.length; pos++) {
            int agente = orden[pos];
            if (esfuerzo + red.costo[agente] <= red.rmax) {
                seleccion[agente] = 1;
                esfuerzo += red.costo[agente];
            }
        }
        int mejorIndividual = buscarMejorAgenteIndividual(red, orden);
        if (mejorIndividual >= 0) {
            int[] individual=new int[red.n]; individual[mejorIndividual]=1;
            seleccion=mejorEstrategia(red,seleccion,individual);
        }
        return seleccion;
    }

    private static int[] mejorarConIntercambio(RedSocial red, int[] inicial) {
        int seleccionado=-1, entrante=-1;
        double aporteBase=aporteSeleccionado(red,inicial);
        double mejorAporte=aporteBase;
        long esfuerzoBase=red.esfuerzo(inicial), mejorEsfuerzo=esfuerzoBase;
        for (int sale=0;sale<red.n;sale++) if (inicial[sale]==1) {
            for (int entra=0;entra<red.n;entra++) if (inicial[entra]==0 && red.aporte[entra]>0) {
                long esfuerzo=esfuerzoBase-red.costo[sale]+red.costo[entra];
                if (esfuerzo>red.rmax) continue;
                double aporte=aporteBase-red.aporte[sale]+red.aporte[entra];
                if (esMejor( aporte, esfuerzo, mejorAporte, mejorEsfuerzo)) {
                    mejorAporte=aporte; mejorEsfuerzo=esfuerzo; seleccionado=sale; entrante=entra;
                }
            }
        }
        int[] resultado=inicial.clone();
        if (seleccionado>=0) { resultado[seleccionado]=0; resultado[entrante]=1; }
        return resultado;
    }

    private static int[] enumerarPrefijo(RedSocial red, Integer[] orden, int k) {
        int prefijo=Math.min(k,orden.length);
        int combinaciones=1<<prefijo;
        int[] mejor=new int[red.n];
        for (int mascara=0;mascara<combinaciones;mascara++) {
            int[] seleccion=new int[red.n]; long esfuerzo=0;
            boolean aplicable=true;
            for(int bit=0;bit<prefijo;bit++) if((mascara&(1<<bit))!=0) {
                int agente=orden[bit]; esfuerzo+=red.costo[agente];
                if(esfuerzo>red.rmax){aplicable=false;break;}
                seleccion[agente]=1;
            }
            if(!aplicable) continue;
            for(int pos=prefijo;pos<orden.length;pos++) {
                int agente=orden[pos];
                if(esfuerzo+red.costo[agente]<=red.rmax){seleccion[agente]=1;esfuerzo+=red.costo[agente];}
            }
            mejor=mejorEstrategia(red,mejor,seleccion);
        }
        int individual=buscarMejorAgenteIndividual(red,orden);
        if(individual>=0){int[] solo=new int[red.n];solo[individual]=1;mejor=mejorEstrategia(red,mejor,solo);}
        return mejor;
    }

    private static int[] mejorEstrategia(RedSocial red,int[] a,int[] b) {
        double aporteA=aporteSeleccionado(red,a), aporteB=aporteSeleccionado(red,b);
        long esfuerzoA=red.esfuerzo(a), esfuerzoB=red.esfuerzo(b);
        return esMejor(aporteB,esfuerzoB,aporteA,esfuerzoA)?b:a;
    }

    private static double aporteSeleccionado(RedSocial red,int[] estrategia) {
        double suma=0; for(int i=0;i<red.n;i++) if(estrategia[i]==1)suma+=red.aporte[i]; return suma;
    }

    /** Devuelve los agentes útiles que caben por sí solos en el presupuesto. */
    private static Integer[] crearCandidatos(RedSocial red) {
        Integer[] candidatosTemporales = new Integer[red.n];
        int cantidad = 0;

        for (int agente = 0; agente < red.n; agente++) {
            boolean tieneAporte = red.aporte[agente] > 0;
            boolean cabeSolo = red.costo[agente] <= red.rmax;
            if (tieneAporte && cabeSolo) {
                candidatosTemporales[cantidad] = agente;
                cantidad++;
            }
        }

        return Arrays.copyOf(candidatosTemporales, cantidad);
    }

    /** Ordena por mayor razón aporte/costo; resuelve empates por aporte e índice. */
    private static int compararCandidatos(RedSocial red, int agenteA, int agenteB) {
        int ordenPorRazon = Double.compare(razonAporteCosto(red, agenteB),
                razonAporteCosto(red, agenteA));
        if (ordenPorRazon != 0) {
            return ordenPorRazon;
        }

        int ordenPorAporte = Double.compare(red.aporte[agenteB], red.aporte[agenteA]);
        if (ordenPorAporte != 0) {
            return ordenPorAporte;
        }
        return Integer.compare(agenteA, agenteB);
    }

    /** Un agente de costo cero tiene prioridad máxima si su aporte es positivo. */
    private static double razonAporteCosto(RedSocial red, int agente) {
        if (red.costo[agente] == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return red.aporte[agente] / red.costo[agente];
    }

    private static int buscarMejorAgenteIndividual(RedSocial red, Integer[] candidatos) {
        int mejorAgente = -1;
        for (int agente : candidatos) {
            if (mejorAgente < 0 || red.aporte[agente] > red.aporte[mejorAgente]) {
                mejorAgente = agente;
            }
        }
        return mejorAgente;
    }

    // ---------------------------------------------------------------------
    // Programación dinámica

    /**
     * Resuelve la mochila 0/1 con una fila de valores y una tabla de decisiones.
     *
     * mejorAporte[presupuesto] guarda el máximo aporte que se puede eliminar con
     * los agentes ya procesados. Se recorren los presupuestos de mayor a menor
     * para que cada agente se use como máximo una vez. La tabla de decisiones
     * permite reconstruir qué agentes formaron la solución.
     */
    public static Solucion modexPD(RedSocial red) { return modexPD(red, progreso -> { }); }

    /** PD con notificaciones opcionales de progreso entre 0 y 100. */
    public static Solucion modexPD(RedSocial red, IntConsumer reportarProgreso) {
        long inicio=System.nanoTime();
        int[] agentesUtiles=new int[red.n]; int cantidad=0; long sumaCostos=0;
        double aporteGratis=0; int[] estrategia=new int[red.n];
        for(int i=0;i<red.n;i++) if(red.aporte[i]>0 && red.costo[i]<=red.rmax) {
            if(red.costo[i]==0){estrategia[i]=1;aporteGratis+=red.aporte[i];}
            else {agentesUtiles[cantidad++]=i;sumaCostos+=red.costo[i];}
        }
        int presupuesto=(int)Math.min((long)red.rmax,sumaCostos);
        int[] indice=new int[cantidad]; System.arraycopy(agentesUtiles,0,indice,0,cantidad);
        long palabras=(presupuesto+64L)/64L;
        long memoria=estimarMemoriaPD(cantidad,presupuesto,palabras);
        long maxHeap=Runtime.getRuntime().maxMemory();
        long reserva=Math.max(RESERVA_HEAP_PD,maxHeap/20);
        long memoriaDisponible=memoriaDisponibleHeap();
        if(memoria>memoriaDisponible-reserva) {
            // Una ejecución anterior puede haber dejado tablas grandes listas para GC.
            System.gc();
            memoriaDisponible=memoriaDisponibleHeap();
        }
        if(memoria>memoriaDisponible-reserva) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "Omitido por memoria: PD necesita aprox. %s; heap máximo %s, disponible ahora %s. Aumente -Xmx o reduzca Rmax.",
                    bytesLegibles(memoria),bytesLegibles(maxHeap),bytesLegibles(Math.max(0,memoriaDisponible))));
        }
        long[][] decisiones=new long[cantidad][(int)palabras];
        double[] mejorAporte=new double[presupuesto+1];
        Arrays.fill(mejorAporte,Double.NEGATIVE_INFINITY);
        mejorAporte[0]=aporteGratis;
        long costoProcesado=0;
        for(int fila=0;fila<cantidad;fila++) {
            int agente=indice[fila], costo=red.costo[agente]; double aporte=red.aporte[agente];
            long limite=Math.min((long)presupuesto,costoProcesado+costo);
            for(int w=(int)limite;w>=costo;w--) {
                int previo=w-costo;
                if(mejorAporte[previo]==Double.NEGATIVE_INFINITY) continue;
                double candidato=mejorAporte[previo]+aporte;
                if(mejorAporte[w]==Double.NEGATIVE_INFINITY || esMejor(candidato,w,mejorAporte[w],w)) {
                    mejorAporte[w]=candidato;
                    decisiones[fila][w>>>6]|=1L<<(w&63);
                }
            }
            costoProcesado=Math.min((long)presupuesto,costoProcesado+costo);
            if(fila%Math.max(1,cantidad/100)==0 || fila==cantidad-1)
                reportarProgreso.accept(cantidad==0?100:(int)((fila+1L)*100/cantidad));
        }
        // Con DP de costo exacto, al final se elige el mejor presupuesto factible;
        // al empatar, recorrer en orden creciente conserva el esfuerzo menor.
        int restante=0;
        for(int w=1;w<=presupuesto;w++) {
            if(mejorAporte[w]==Double.NEGATIVE_INFINITY) continue;
            if(esMejor(mejorAporte[w],w,mejorAporte[restante],restante)) restante=w;
        }
        for(int fila=cantidad-1;fila>=0;fila--) {
            if((decisiones[fila][restante>>>6]&(1L<<(restante&63)))!=0) {
                int agente=indice[fila]; estrategia[agente]=1; restante-=red.costo[agente];
            }
        }
        reportarProgreso.accept(100);
        return Solucion.crear("Programacion dinamica",red,estrategia,System.nanoTime()-inicio);
    }

    /** Estima almacenamiento de decisiones bit a bit, valores y estructuras auxiliares. */
    public static long estimarMemoriaPD(RedSocial red) {
        int activos=0; long suma=0;
        for(int i=0;i<red.n;i++) if(red.aporte[i]>0 && red.costo[i]>0 && red.costo[i]<=red.rmax){activos++;suma+=red.costo[i];}
        int presupuesto=(int)Math.min((long)red.rmax,suma);
        return estimarMemoriaPD(activos,presupuesto,(presupuesto+64L)/64L);
    }

    private static long estimarMemoriaPD(int filas,int presupuesto,long palabras) {
        return 24L*filas
                + Math.multiplyExact(Math.multiplyExact((long)filas,palabras),8L)
                + Math.multiplyExact((long)presupuesto+1,16L);
    }

    public static String motivoOmitirPD(RedSocial red) {
        long requeridos=estimarMemoriaPD(red); Runtime rt=Runtime.getRuntime();
        long disponible=memoriaDisponibleHeap();
        long reserva=Math.max(RESERVA_HEAP_PD,rt.maxMemory()/20);
        if(requeridos>disponible-reserva){System.gc();disponible=memoriaDisponibleHeap();}
        return requeridos>disponible-reserva
                ? "Omitido por memoria: requiere aprox. "+bytesLegibles(requeridos)+"; aumente -Xmx o reduzca Rmax."
                : null;
    }

    private static long memoriaDisponibleHeap() {
        Runtime rt=Runtime.getRuntime();
        return rt.maxMemory()-(rt.totalMemory()-rt.freeMemory());
    }

    private static String bytesLegibles(long bytes) {
        if(bytes>=1024L*1024*1024)return String.format(Locale.ROOT,"%.2f GiB",bytes/(1024d*1024*1024));
        return String.format(Locale.ROOT,"%.1f MiB",bytes/(1024d*1024));
    }
}
