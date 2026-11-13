package co.umariana.construccion;

import co.umariana.modelo.ArbolGenealogico;
import co.umariana.modelo.ArbolInvalidoException;
import co.umariana.modelo.Persona;
import co.umariana.modelo.Posicion;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye un árbol genealógico a partir de un texto: cada línea es una persona.
 *
 *     posicion;nombre;nacimiento;fallecimiento
 *
 * Ejemplo (el fallecimiento puede quedar vacío):
 *
 *     1;John Doe;2002;
 *     2;Henry Doe;1920;1990
 *     3;Maria Doe;1921;
 *     1.1;Timmy Doe;2021;
 *     1.2;Amy Doe;2026;
 *
 * Funciona en dos pasadas:
 *   1. LEER: cada línea se convierte en una Persona (con su número de línea) y se valida.
 *   2. ARMAR: las personas se ordenan para que cada una llegue DESPUÉS de la que necesita
 *      (sujeto, ascendientes por número Ahnentafel y descendientes por generación) y se agregan al árbol,
 *      que es quien valida las reglas (repetidos, progenitor faltante, años de nacimiento).
 */
public final class ConstructorArbol {

    /** Una persona leída del texto junto con la línea de la que salió (para informar los errores). */
    private record Entrada(int linea, Persona persona) {
    }

    private ConstructorArbol() {
    }

    /**
     * Convierte el texto en un árbol (ver las reglas arriba).
     *
     * @throws ArbolInvalidoException si el texto no cumple las reglas
     */
    public static ArbolGenealogico construir(String texto) {
        List<Entrada> entradas = leer(texto);

        if (entradas.isEmpty()) {
            throw new ArbolInvalidoException("El texto está vacío: escriba al menos la persona 1 (el sujeto).");
        }

        // List.sort es ESTABLE: las posiciones repetidas conservan el orden del texto,
        // así el error se informa en la línea de la segunda aparición.
        entradas.sort((a, b) -> Integer.compare(clave(a.persona().getPosicion()), clave(b.persona().getPosicion())));

        // Tras ordenar, el sujeto (clave 0) queda primero si existe.
        Entrada primera = entradas.get(0);
        if (!Posicion.esSujeto(primera.persona().getPosicion())) {
            throw new ArbolInvalidoException("Falta la persona de la posición 1 (el sujeto del árbol).");
        }

        ArbolGenealogico arbol = new ArbolGenealogico(primera.persona());
        for (int i = 1; i < entradas.size(); i++) {
            Entrada e = entradas.get(i);
            try {
                arbol.agregar(e.persona());
            } catch (IllegalArgumentException ex) {   // incluye ArbolInvalidoException
                throw new ArbolInvalidoException("Línea " + e.linea() + ": " + ex.getMessage());
            }
        }
        return arbol;
    }

    /** Primera pasada: convierte cada línea con contenido en una Persona válida. */
    private static List<Entrada> leer(String texto) {
        List<Entrada> entradas = new ArrayList<>();
        if (texto == null) {
            return entradas;
        }
        // -1: conserva las líneas vacías del final; así los números de línea cuentan TODAS las líneas.
        String[] lineas = texto.split("\r\n|\n|\r", -1);
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isEmpty()) {
                continue;
            }
            int numero = i + 1;
            try {
                entradas.add(new Entrada(numero, leerLinea(linea)));
            } catch (IllegalArgumentException ex) {   // incluye ArbolInvalidoException
                throw new ArbolInvalidoException("Línea " + numero + ": " + ex.getMessage());
            }
        }
        return entradas;
    }

    /** Convierte "posicion;nombre;nacimiento;fallecimiento" en una Persona ya validada. */
    private static Persona leerLinea(String linea) {
        String[] campos = linea.split(";", -1);   // -1: conserva el campo final vacío
        if (campos.length < 3 || campos.length > 4) {
            throw new ArbolInvalidoException("se esperaban 3 o 4 campos separados por \";\" "
                    + "(posicion;nombre;nacimiento;fallecimiento) y se encontraron " + campos.length + ".");
        }
        String posicion = campos[0].trim();
        String nombre = campos[1].trim();
        int nacimiento = entero(campos[2], "nacimiento");
        String textoFallecimiento = campos.length == 4 ? campos[3].trim() : "";
        Integer fallecimiento = textoFallecimiento.isEmpty() ? null : entero(textoFallecimiento, "fallecimiento");

        Persona persona = new Persona(posicion, nombre, nacimiento, fallecimiento);
        persona.validar();
        return persona;
    }

    private static int entero(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new ArbolInvalidoException("el año de " + campo + " \"" + valor.trim()
                    + "\" no es un número entero.");
        }
    }

    /**
     * Clave de orden de inserción: el sujeto primero (0); luego los ascendientes por número Ahnentafel
     * (2..2047, así el hijo n/2 siempre va antes que n); luego los descendientes por generación
     * (el progenitor siempre tiene una generación menos que su hijo).
     */
    private static int clave(String posicion) {
        if (Posicion.esSujeto(posicion)) {
            return 0;
        }
        if (Posicion.esAscendiente(posicion)) {
            return Integer.parseInt(posicion);
        }
        return Posicion.MAX_AHNENTAFEL + Posicion.generacion(posicion);
    }
}