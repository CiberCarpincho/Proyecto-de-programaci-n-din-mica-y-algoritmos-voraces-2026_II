package modex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Benchmark reproducible de heurísticas voraces contra la DP exacta. */
public final class BenchmarkModex {
    private BenchmarkModex() { }

    private static final int[] VARIANTES={0,1,2,3};
    private static final String[] NOMBRES={"Razón + mejor individual","Dos órdenes","Dos órdenes + 1-1","Prefijo exacto k=6"};

    public static void ejecutar() {
        Locale.setDefault(Locale.US);
        int[] tamanos={20,50,200,1000};
        int[] cantidades={200,100,50,20};
        // Calienta JIT con el generador y todas las heurísticas antes de cronometrar.
        for(int i=0;i<150;i++){
            RedSocial red=Generador.generar(20,90000L+i,.5);
            for(int variante:VARIANTES)Modex.modexVVariante(red,variante);
            Modex.modexPD(red);
        }
        System.out.println("Semilla base=6102026; Rmax=ceil(0.5*costoTotal); tiempos sin archivos (mediana por caso y luego entre casos).");
        System.out.println("n,casos,heuristica,brecha_media_pct,brecha_max_pct,optimos_pct,tiempo_mediana_ms,PD_mediana_ms");
        for(int si=0;si<tamanos.length;si++){
            int n=tamanos[si],casos=cantidades[si];
            @SuppressWarnings("unchecked") List<Double>[] gaps=new List[4],tiempos=new List[4];
            for(int v=0;v<4;v++){gaps[v]=new ArrayList<>();tiempos[v]=new ArrayList<>();}
            List<Double> tiemposPD=new ArrayList<>();
            double[] sumaGap=new double[4],maxGap=new double[4]; int[] optimos=new int[4];
            for(int caso=0;caso<casos;caso++){
                RedSocial red=Generador.generar(n,6102026L+((long)n*100000)+caso,.5);
                Solucion optimo=Modex.modexPD(red);
                double aporteOptimo=aporte(red,optimo.e);
                tiemposPD.add(optimo.milisegundos());
                for(int v=0;v<4;v++){
                    long[] muestras=new long[5]; int[] estrategia=null;
                    for(int rep=0;rep<muestras.length;rep++){
                        long inicio=System.nanoTime();
                        estrategia=Modex.modexVVariante(red,VARIANTES[v]);
                        muestras[rep]=System.nanoTime()-inicio;
                    }
                    double aporte=aporte(red,estrategia);
                    double gap=optimo.extremismo>0
                            ? (Solucion.crear("h",red,estrategia,0).extremismo-optimo.extremismo)/optimo.extremismo*100
                            : (aporteOptimo-aporte<=1e-9?0:100);
                    gaps[v].add(gap); tiempos[v].add(mediana(muestras));
                    sumaGap[v]+=gap; maxGap[v]=Math.max(maxGap[v],gap);
                    if(Math.abs(aporteOptimo-aporte)<=1e-7)optimos[v]++;
                }
            }
            double tiempoPD=mediana(tiemposPD);
            for(int v=0;v<4;v++){
                System.out.printf(Locale.US,"%d,%d,%s,%.5f,%.5f,%.2f,%.4f,%.4f%n",n,casos,NOMBRES[v],
                        sumaGap[v]/casos,maxGap[v],100.0*optimos[v]/casos,mediana(tiempos[v]),tiempoPD);
            }
        }
    }

    private static double aporte(RedSocial red,int[] estrategia){
        double suma=0;for(int i=0;i<red.n;i++)if(estrategia[i]==1)suma+=red.aporte[i];return suma;
    }
    private static double mediana(long[] values){
        long[] copia=values.clone();java.util.Arrays.sort(copia);
        return copia.length%2==1?copia[copia.length/2]/1e6:(copia[copia.length/2-1]/2e6+copia[copia.length/2]/2e6);
    }
    private static double mediana(List<Double> values){
        List<Double> copia=new ArrayList<>(values);Collections.sort(copia);
        return copia.size()%2==1?copia.get(copia.size()/2):(copia.get(copia.size()/2-1)+copia.get(copia.size()/2))/2;
    }
}
