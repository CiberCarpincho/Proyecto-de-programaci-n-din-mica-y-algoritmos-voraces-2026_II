package modex;

import java.util.Arrays;
import java.util.Random;

/** Pruebas automáticas: java -jar ModEx.jar --pruebas. */
public final class Pruebas {
    private Pruebas() { }
    private static int fallos;

    private static void check(boolean ok,String mensaje) {
        if(!ok){fallos++;System.out.println("  [FALLO] "+mensaje);}
    }
    private static boolean cerca(double a,double b){return Math.abs(a-b)<1e-6;}
    private static void compararExactos(RedSocial r,String caso){
        Solucion fb=Modex.modexFB(r),pd=Modex.modexPD(r);
        check(cerca(fb.extremismo,pd.extremismo),caso+": FB y PD deben coincidir");
        check(fb.esfuerzo==pd.esfuerzo,caso+": FB y PD deben desempatar por menor esfuerzo");
        check(r.aplicable(fb.e)&&r.aplicable(pd.e),caso+": estrategias exactas aplicables");
    }

    public static boolean ejecutar() {
        fallos=0;
        System.out.println("== Tabla de categorías y propósito ==");
        System.out.println("Categoría                              | Qué comprueba");
        System.out.println("Presupuesto cero / total              | Factibilidad de extremos");
        System.out.println("n=0, n=1 y aportes cero               | Casos vacíos y degenerados");
        System.out.println("Costo cero / costo igual              | Selección gratis y voraz conocido");
        System.out.println("Empate de aporte / frontera exacta    | Menor esfuerzo y <= Rmax");
        System.out.println("Contraejemplo voraz                    | Heurística no supera el óptimo");
        System.out.println("2.000 redes reproducibles             | FB=PD; voraz no mejora al óptimo");

        System.out.println("== Ejemplos del enunciado ==");
        RedSocial rs1=new RedSocial(new double[]{100,100,-10},new double[]{.5,.1,.1},new double[]{1,1,1},55);
        check(rs1.esfuerzo(new int[]{0,0,1})==9,"RS1 E=<0,0,1> esfuerzo 9");
        check(Math.abs(rs1.extremismo(new int[]{0,0,1})-47.14)<.01,"RS1 Ext 47.14");
        check(rs1.esfuerzo(new int[]{1,0,0})==50,"RS1 E=<1,0,0> esfuerzo 50");
        check(Math.abs(rs1.extremismo(new int[]{1,0,0})-33.5)<.01,"RS1 Ext óptimo 33.5");
        compararExactos(rs1,"RS1");

        RedSocial rs2=new RedSocial(new double[]{-30,20,50},new double[]{.9,.1,.5},new double[]{1,2,1},35);
        check(rs2.esfuerzo(new int[]{0,1,0})==36&&!rs2.aplicable(new int[]{0,1,0}),"RS2 costo 36 supera Rmax=35");
        compararExactos(rs2,"RS2");

        System.out.println("== Bordes, empates y frontera ==");
        RedSocial vacia=new RedSocial(new double[0],new double[0],new double[0],0);
        compararExactos(vacia,"n=0");
        check(vacia.extremismo(new int[0])==0,"n=0 tiene Ext=0");
        compararExactos(new RedSocial(new double[]{-7},new double[]{0},new double[]{1},7),"n=1");
        RedSocial ceros=new RedSocial(new double[]{0,0,0},new double[]{0,.5,1},new double[]{1,2,3},0);
        compararExactos(ceros,"opiniones cero y Rmax=0");
        check(Modex.modexPD(ceros).esfuerzo==0,"aportes cero no requieren moderación");
        RedSocial presupuestoCero=new RedSocial(new double[]{10,20},new double[]{1,0},new double[]{1,1},0);
        compararExactos(presupuestoCero,"Rmax=0");
        check(Modex.modexPD(presupuestoCero).e[0]==1,"costo cero cabe con Rmax=0");
        RedSocial presupuestoTotal=new RedSocial(new double[]{10,-20},new double[]{0,0},new double[]{1,1},30);
        compararExactos(presupuestoTotal,"Rmax=costo total");
        check(Arrays.equals(Modex.modexPD(presupuestoTotal).e,new int[]{1,1}),"con presupuesto total se moderan ambos");
        RedSocial costoCero=new RedSocial(new double[]{10,20,30},new double[]{1,1,0},new double[]{1,2,1},0);
        compararExactos(costoCero,"agentes de costo cero");
        check(Modex.modexPD(costoCero).e[0]==1&&Modex.modexPD(costoCero).e[1]==1,"costo cero selecciona aportes positivos");
        RedSocial costosIguales=new RedSocial(new double[]{10,20,30,40},new double[]{0,0,0,0},new double[]{1,1,1,1},60);
        compararExactos(costosIguales,"costos iguales");
        check(cerca(Modex.modexV(costosIguales).extremismo,Modex.modexPD(costosIguales).extremismo),"voraz es óptimo con costos iguales");
        RedSocial empate=new RedSocial(new double[]{10,10},new double[]{.5,0},new double[]{1,1},10);
        compararExactos(empate,"empate de aporte");
        check(Modex.modexPD(empate).esfuerzo==5,"en empate se elige menor esfuerzo");
        RedSocial frontera=new RedSocial(new double[]{10,20,30},new double[]{.6,0,0},new double[]{1,1,1},50);
        compararExactos(frontera,"estrategia exacta en Rmax");

        System.out.println("== Contraejemplo voraz solicitado ==");
        RedSocial contra=new RedSocial(new double[]{10,20,30},new double[]{.6,0,0},new double[]{1,1,1},50);
        Solucion vg=Solucion.crear("Voraz razón",contra,Modex.modexVVariante(contra,0),0),opt=Modex.modexPD(contra);
        check(vg.esfuerzo==34&&cerca(vg.extremismo,6.6666666667),"voraz Ext=6.667 y esfuerzo 34");
        check(opt.esfuerzo==50&&cerca(opt.extremismo,3.3333333333),"óptimo Ext=3.333 y esfuerzo 50");
        check(vg.extremismo>=opt.extremismo-1e-9,"voraz no supera al óptimo");

        System.out.println("== 2.000 casos aleatorios reproducibles: FB vs PD ==");
        Random random=new Random(20261102L); int casos=2000,vorazOptimo=0; double peorBrecha=0;
        for(int t=0;t<casos;t++){
            int n=random.nextInt(15); double fraccion=random.nextDouble();
            RedSocial red=Generador.generar(n,random.nextLong(),fraccion);
            Solucion fb=Modex.modexFB(red),pd=Modex.modexPD(red),v=Modex.modexV(red);
            check(red.aplicable(fb.e)&&red.aplicable(pd.e)&&red.aplicable(v.e),"aplicabilidad caso "+t);
            check(cerca(fb.extremismo,pd.extremismo),"FB != PD caso "+t);
            check(fb.esfuerzo==pd.esfuerzo,"desempate FB != PD caso "+t);
            check(v.extremismo>=pd.extremismo-1e-9,"voraz mejora imposible caso "+t);
            if(cerca(v.extremismo,pd.extremismo))vorazOptimo++;
            else if(pd.extremismo>0)peorBrecha=Math.max(peorBrecha,(v.extremismo-pd.extremismo)/pd.extremismo);
        }
        System.out.printf("  Voraz óptimo: %d/%d; peor brecha Ext: %.3f%%%n",vorazOptimo,casos,peorBrecha*100);
        System.out.println(fallos==0?"TODAS LAS PRUEBAS PASARON":fallos+" PRUEBA(S) FALLARON");
        return fallos==0;
    }
}
