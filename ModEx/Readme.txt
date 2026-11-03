PROYECTO ModEx - Moderando el extremismo de opiniones en una red social
Analisis y Diseno de Algoritmos II

REQUISITOS
  Java 11 o superior (JRE o JDK) en Windows, macOS o Linux.
  El mismo ModEx.jar funciona en todos los sistemas operativos.

CONTENIDO
  ModEx.jar            Programa ejecutable (incluye interfaz grafica).
  src/modex/           Codigo fuente:
    RedSocial.java       Modelo de la red, costo por agente, esfuerzo y extremismo.
    Solucion.java        Tripleta (E, Esfuerzo, Ext) + nombre del algoritmo y tiempo.
    Modex.java           modexFB (fuerza bruta), modexV (voraz), modexPD (prog. dinamica).
    ArchivoIO.java       Lectura/escritura de los formatos de entrada y salida.
    Generador.java       Generador de casos de prueba aleatorios (con semilla).
    ModexGUI.java        Interfaz grafica (Swing).
    Pruebas.java         Pruebas automaticas.
    Main.java            Punto de entrada (interfaz o modo consola).
  ejemplos/            Entradas de ejemplo (ejemplo1.txt y ejemplo2.txt son los del enunciado).
  compilar.sh / compilar.bat   Recompilan el codigo y regeneran ModEx.jar.
  Readme.txt           Este archivo.

EJECUCION
  Interfaz grafica:   doble clic en ModEx.jar   o   java -jar ModEx.jar
  Modo consola:       java -jar ModEx.jar entrada.txt salida.txt [FB|V|PD]
  Pruebas:            java -jar ModEx.jar --pruebas
  Si una entrada grande agota la memoria:  java -Xmx4g -jar ModEx.jar

USO DE LA INTERFAZ
  Cargar entrada...            Lee un archivo de entrada y lo muestra en la tabla.
  Generar caso...              Crea una entrada aleatoria (n, semilla, Rmax como fraccion del esfuerzo total)
                               y permite guardarla como archivo.
  Algoritmo + Ejecutar         Corre modexFB, modexV o modexPD. Los agentes moderados se resaltan en verde.
  Comparar los 3               Corre los tres y muestra Ext, esfuerzo, tiempo y diferencia frente al mejor.
  Verificar costo              Desglosa el calculo del esfuerzo (agente por agente) y del extremismo.
  Guardar salida...            Escribe la salida en el formato del enunciado.
  Cargar salida a verificar... Lee un archivo de salida y recalcula esfuerzo y extremismo para comprobarlos.
  Generar carpeta de resultados... Procesa una carpeta de pruebas y guarda un resumen CSV, las entradas y las salidas calculadas.
  Nota: fuerza bruta esta limitada a n <= 30 (revisa 2^n subconjuntos).

INFORME DE RESULTADOS
  Seleccione la carpeta que contiene las pruebas y luego dónde guardar el informe.
  Se crea una carpeta Resultados_ModEx_fecha_hora con resumen.csv, LEEME.txt,
  copias de las entradas y los archivos de salida de cada algoritmo ejecutado.
  Se agregan automaticamente los ejemplos si existe una carpeta ejemplos junto a la bateria.
  Para terminar en tiempo razonable, el informe omite fuerza bruta con mas de 20 agentes.
  Programacion dinamica se omite si su tabla supera 200 millones de celdas.

FORMATO DE ENTRADA (n + 2 lineas)
  n
  opinion,receptividad,influencia      (una linea por agente, separados por coma, punto decimal)
  ...
  Rmax
  Se ignoran lineas vacias y las que empiezan con #.

FORMATO DE SALIDA (n + 2 lineas)
  Ext        extremismo de la red moderada
  Esf        esfuerzo de la estrategia
  mod0 ... mod(n-1)   0 = no moderado, 1 = moderado

NOTAS
  Ext(RS) = raiz( suma(o_i^2 * inf_i) ) / n. Con esta lectura de la formula se reproducen los valores
  del enunciado (47.14, 33.5 y 9.43).
  El techo del esfuerzo se calcula con una tolerancia de 1e-9 para evitar errores de punto flotante.
