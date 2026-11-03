package modex;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Genera archivos de resultados a partir de una carpeta de casos de prueba. */
public final class GeneradorResultados {
    /** En una ejecución por lotes, limita fuerza bruta a casos que terminan pronto. */
    private static final int MAX_FB_PARA_INFORME = 20;
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private GeneradorResultados() {}

    /** Información que la interfaz muestra al terminar la generación. */
    public static final class InformeGenerado {
        public final Path carpeta;
        public final int cantidadCasos;
        public final int cantidadSoluciones;
        public final int cantidadOmitidas;

        private InformeGenerado(Path carpeta, int cantidadCasos,
                int cantidadSoluciones, int cantidadOmitidas) {
            this.carpeta = carpeta;
            this.cantidadCasos = cantidadCasos;
            this.cantidadSoluciones = cantidadSoluciones;
            this.cantidadOmitidas = cantidadOmitidas;
        }

        public String resumen() {
            return "Resultados guardados en: " + carpeta + "\n"
                    + "Casos procesados: " + cantidadCasos + "\n"
                    + "Soluciones calculadas: " + cantidadSoluciones + "\n"
                    + "Ejecuciones omitidas por tamaño o memoria: " + cantidadOmitidas;
        }
    }

    /**
     * Busca la batería incluida en el proyecto para usarla como carpeta inicial
     * en el selector de la interfaz.
     */
    public static Path buscarCarpetaPruebas() {
        List<Path> lugaresIniciales = new ArrayList<>();
        lugaresIniciales.add(Paths.get(System.getProperty("user.dir")));

        try {
            Path ubicacionCodigo = Paths.get(GeneradorResultados.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(ubicacionCodigo)) {
                ubicacionCodigo = ubicacionCodigo.getParent();
            }
            lugaresIniciales.add(ubicacionCodigo);
        } catch (URISyntaxException | NullPointerException ex) {
            // Si no se puede encontrar la ubicación del programa, se usa user.dir.
        }

        for (Path lugar : lugaresIniciales) {
            Path actual = lugar;
            for (int nivel = 0; actual != null && nivel < 5; nivel++) {
                Path dentroDeModEx = actual.resolve("ModEx").resolve("Bateria de pruebas");
                if (Files.isDirectory(dentroDeModEx)) {
                    return dentroDeModEx;
                }

                Path juntoAlPrograma = actual.resolve("Bateria de pruebas");
                if (Files.isDirectory(juntoAlPrograma)) {
                    return juntoAlPrograma;
                }
                actual = actual.getParent();
            }
        }

        return null;
    }

    /**
     * Ejecuta los algoritmos disponibles para cada archivo .txt y crea una
     * carpeta nueva dentro de la ubicación elegida por el usuario.
     */
    public static InformeGenerado generar(Path carpetaPruebas, Path carpetaDestino)
            throws IOException {
        List<Path> casos = buscarCasos(carpetaPruebas);
        if (casos.isEmpty()) {
            throw new IOException("La carpeta seleccionada no contiene archivos .txt.");
        }

        Path carpetaResultados = crearCarpetaResultados(carpetaDestino);
        Path carpetaEntradas = Files.createDirectory(carpetaResultados.resolve("entradas"));
        Path carpetaSalidas = Files.createDirectory(carpetaResultados.resolve("salidas"));
        List<String> filasResumen = new ArrayList<>();
        List<String> notas = new ArrayList<>();
        int solucionesCalculadas = 0;
        int ejecucionesOmitidas = 0;

        for (Path archivoCaso : casos) {
            RedSocial red = ArchivoIO.leerEntrada(archivoCaso);
            String nombreCaso = nombreSinExtension(archivoCaso);
            Files.copy(archivoCaso, carpetaEntradas.resolve(nombreCaso + ".txt"),
                    StandardCopyOption.REPLACE_EXISTING);

            List<ResultadoAlgoritmo> resultados = ejecutarAlgoritmos(red);
            double mejorExtremismo = mejorExtremismo(resultados);

            for (ResultadoAlgoritmo resultado : resultados) {
                if (resultado.solucion == null) {
                    ejecucionesOmitidas++;
                    filasResumen.add(crearFilaCSV(nombreCaso, red.n, resultado.nombre,
                            "OMITIDO", "", "", red.rmax, "", "", resultado.detalle));
                    notas.add(nombreCaso + " - " + resultado.nombre + ": " + resultado.detalle);
                    continue;
                }

                solucionesCalculadas++;
                Solucion solucion = resultado.solucion;
                double brecha = calcularBrecha(solucion.extremismo, mejorExtremismo);
                String nombreSalida = nombreCaso + "_" + resultado.clave + ".txt";
                ArchivoIO.escribirSalida(carpetaSalidas.resolve(nombreSalida), solucion);

                filasResumen.add(crearFilaCSV(nombreCaso, red.n, resultado.nombre,
                        "OK", formatoDecimal(solucion.extremismo),
                        String.valueOf(solucion.esfuerzo), red.rmax,
                        formatoDecimal(solucion.milisegundos()), formatoDecimal(brecha), ""));
            }
        }

        escribirResumen(carpetaResultados, filasResumen);
        escribirNotas(carpetaResultados, casos.size(), solucionesCalculadas,
                ejecucionesOmitidas, notas);

        return new InformeGenerado(carpetaResultados, casos.size(),
                solucionesCalculadas, ejecucionesOmitidas);
    }

    /** Reúne los .txt de la carpeta seleccionada y los ejemplos que la acompañan. */
    private static List<Path> buscarCasos(Path carpetaPruebas) throws IOException {
        if (!Files.isDirectory(carpetaPruebas)) {
            throw new IOException("La carpeta de pruebas no existe o no se puede leer.");
        }

        List<Path> carpetas = new ArrayList<>();
        carpetas.add(carpetaPruebas);
        Path carpetaEjemplos = carpetaPruebas.getParent() == null
                ? null : carpetaPruebas.getParent().resolve("ejemplos");
        if (carpetaEjemplos != null && Files.isDirectory(carpetaEjemplos)) {
            carpetas.add(carpetaEjemplos);
        }

        List<Path> casos = new ArrayList<>();
        for (Path carpeta : carpetas) {
            try (java.util.stream.Stream<Path> archivos = Files.list(carpeta)) {
                casos.addAll(archivos
                        .filter(Files::isRegularFile)
                        .filter(archivo -> archivo.getFileName().toString()
                                .toLowerCase(Locale.ROOT).endsWith(".txt"))
                        .collect(Collectors.toList()));
            }
        }

        casos.sort(Comparator
                .comparingInt((Path archivo) -> numeroEnNombre(archivo.getFileName().toString()))
                .thenComparing(archivo -> archivo.getFileName().toString(),
                        String.CASE_INSENSITIVE_ORDER));
        return casos;
    }

    /** Ejecuta voraz siempre y los algoritmos exactos cuando el tamaño lo permite. */
    private static List<ResultadoAlgoritmo> ejecutarAlgoritmos(RedSocial red) {
        List<ResultadoAlgoritmo> resultados = new ArrayList<>();

        if (red.n <= MAX_FB_PARA_INFORME) {
            resultados.add(ejecutar("Fuerza bruta", "FB", () -> Modex.modexFB(red)));
        } else {
            resultados.add(ResultadoAlgoritmo.omitido("Fuerza bruta", "FB",
                    "Omitido por tamaño: el informe por lotes limita fuerza bruta a n <= "
                    + MAX_FB_PARA_INFORME + " para evitar tiempos exponenciales."));
        }

        resultados.add(ejecutar("Voraz", "V", () -> Modex.modexV(red)));

        String motivoMemoria = Modex.motivoOmitirPD(red);
        if (motivoMemoria != null) {
            resultados.add(ResultadoAlgoritmo.omitido("Programación dinámica", "PD",
                    motivoMemoria));
        } else {
            resultados.add(ejecutar("Programación dinámica", "PD",
                    () -> Modex.modexPD(red)));
        }

        return resultados;
    }

    private static ResultadoAlgoritmo ejecutar(String nombre, String clave,
            TareaAlgoritmo tarea) {
        try {
            return ResultadoAlgoritmo.completado(nombre, clave, tarea.ejecutar());
        } catch (OutOfMemoryError error) {
            return ResultadoAlgoritmo.omitido(nombre, clave,
                    "No hubo memoria suficiente para ejecutar el algoritmo.");
        } catch (RuntimeException error) {
            return ResultadoAlgoritmo.omitido(nombre, clave,
                    "No se pudo ejecutar: " + error.getMessage());
        }
    }

    private static double mejorExtremismo(List<ResultadoAlgoritmo> resultados) {
        double mejor = Double.POSITIVE_INFINITY;
        for (ResultadoAlgoritmo resultado : resultados) {
            if (resultado.solucion != null) {
                mejor = Math.min(mejor, resultado.solucion.extremismo);
            }
        }
        return mejor;
    }

    /** Diferencia porcentual respecto de la mejor solución calculada para el caso. */
    private static double calcularBrecha(double extremismo, double mejorExtremismo) {
        if (mejorExtremismo <= 1e-12) {
            return 0;
        }
        return (extremismo - mejorExtremismo) / mejorExtremismo * 100;
    }

    private static void escribirResumen(Path carpetaResultados, List<String> filas)
            throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("caso,n,algoritmo,estado,ext,esfuerzo,rmax,tiempo_ms,brecha_vs_mejor_pct,detalle");
        lineas.addAll(filas);
        Files.write(carpetaResultados.resolve("resumen.csv"), lineas, StandardCharsets.UTF_8);
    }

    private static String crearFilaCSV(String caso, int n, String algoritmo, String estado,
            String extremismo, String esfuerzo, int presupuesto, String tiempo,
            String brecha, String detalle) {
        return unirCSV(caso, String.valueOf(n), algoritmo, estado, extremismo,
                esfuerzo, String.valueOf(presupuesto), tiempo, brecha, detalle);
    }

    private static String unirCSV(String... columnas) {
        StringBuilder fila = new StringBuilder();
        for (int indice = 0; indice < columnas.length; indice++) {
            if (indice > 0) {
                fila.append(',');
            }
            fila.append('"').append(columnas[indice].replace("\"", "\"\"")).append('"');
        }
        return fila.toString();
    }

    private static void escribirNotas(Path carpetaResultados, int cantidadCasos,
            int soluciones, int omitidas, List<String> notas) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("Resultados de ModEx");
        lineas.add("");
        lineas.add("Casos procesados: " + cantidadCasos);
        lineas.add("Soluciones calculadas: " + soluciones);
        lineas.add("Ejecuciones omitidas: " + omitidas);
        lineas.add("");
        lineas.add("Archivos:");
        lineas.add("- resumen.csv: métricas y estado de cada algoritmo por caso.");
        lineas.add("- entradas/: copias de los archivos usados.");
        lineas.add("- salidas/: archivos de salida escritos en el formato de ModEx.");
        lineas.add("");
        lineas.add("Los tiempos dependen del equipo y de la carga del sistema.");
        lineas.add("La brecha se calcula frente al menor extremismo obtenido entre los algoritmos ejecutados para ese caso.");
        lineas.add("Si se omitieron los algoritmos exactos, esta brecha no representa la diferencia frente al óptimo global.");
        lineas.add("Fuerza bruta se omite en informes por lotes con más de "
                + MAX_FB_PARA_INFORME + " agentes. Programación dinámica se omite si la memoria estimada excede el heap disponible.");

        if (!notas.isEmpty()) {
            lineas.add("");
            lineas.add("Ejecuciones omitidas:");
            lineas.addAll(notas);
        }

        Files.write(carpetaResultados.resolve("LEEME.txt"), lineas, StandardCharsets.UTF_8);
    }

    private static Path crearCarpetaResultados(Path carpetaDestino) throws IOException {
        if (!Files.isDirectory(carpetaDestino)) {
            throw new IOException("La carpeta de destino no existe o no se puede escribir.");
        }

        String nombreBase = "Resultados_ModEx_" + LocalDateTime.now().format(FORMATO_FECHA);
        Path carpeta = carpetaDestino.resolve(nombreBase);
        int numero = 2;
        while (Files.exists(carpeta)) {
            carpeta = carpetaDestino.resolve(nombreBase + "_" + numero);
            numero++;
        }
        return Files.createDirectory(carpeta);
    }

    private static String nombreSinExtension(Path archivo) {
        String nombre = archivo.getFileName().toString();
        return nombre.substring(0, nombre.length() - 4);
    }

    private static int numeroEnNombre(String nombre) {
        String digitosFinales = nombre.replaceFirst("\\.[^.]*$", "")
                .replaceFirst("^.*?(\\d+)$", "$1");
        try {
            return Integer.parseInt(digitosFinales);
        } catch (NumberFormatException ex) {
            return Integer.MAX_VALUE;
        }
    }

    private static String formatoDecimal(double valor) {
        return String.format(Locale.US, "%.6f", valor);
    }

    private interface TareaAlgoritmo {
        Solucion ejecutar();
    }

    private static final class ResultadoAlgoritmo {
        final String nombre;
        final String clave;
        final Solucion solucion;
        final String detalle;

        private ResultadoAlgoritmo(String nombre, String clave,
                Solucion solucion, String detalle) {
            this.nombre = nombre;
            this.clave = clave;
            this.solucion = solucion;
            this.detalle = detalle;
        }

        static ResultadoAlgoritmo completado(String nombre, String clave, Solucion solucion) {
            return new ResultadoAlgoritmo(nombre, clave, solucion, "");
        }

        static ResultadoAlgoritmo omitido(String nombre, String clave, String detalle) {
            return new ResultadoAlgoritmo(nombre, clave, null, detalle);
        }
    }
}
