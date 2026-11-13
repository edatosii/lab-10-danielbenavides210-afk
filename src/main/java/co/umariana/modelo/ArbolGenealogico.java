package co.umariana.modelo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 * El árbol genealógico: un SUJETO (posición "1") con sus ASCENDIENTES (padres, abuelos... en Ahnentafel)
 * y sus DESCENDIENTES (hijos, nietos... en d'Aboville). Ver la clase Posicion.
 *
 * Son en realidad dos árboles unidos en la raíz:
 *
 *     ascendientes:  árbol BINARIO. Cada persona tiene como mucho un padre (2n) y una madre (2n + 1).
 *     descendientes: árbol GENERAL. Cada persona tiene una lista de hijos (1.1, 1.2, ...) ordenada por número.
 *
 * El árbol solo guarda a los miembros que EXISTEN: no hay "huecos" ni personas vacías.
 *
 * Estructura: cada miembro es un Nodo propio. La raíz (el sujeto) usa a la vez los enlaces padre/madre
 * (hacia arriba) y la lista de hijos (hacia abajo). Los ascendientes solo usan padre/madre y los
 * descendientes solo usan su lista de hijos.
 */
public class ArbolGenealogico {

    /** Un miembro del árbol con sus enlaces. */
    private static final class Nodo {
        final Persona persona;
        Nodo padre;                                   // posición 2n   (solo sujeto y ascendientes)
        Nodo madre;                                   // posición 2n+1 (solo sujeto y ascendientes)
        final List<Nodo> hijos = new ArrayList<>();   // ordenada por número de orden (solo sujeto y descendientes)

        Nodo(Persona persona) {
            this.persona = persona;
        }

        /** Número de orden de este nodo entre sus hermanos (último número de su posición d'Aboville). */
        int orden() {
            return Posicion.orden(persona.getPosicion());
        }
    }

    private final Nodo raiz;
    private int cantidad;

    /**
     * Crea un árbol que solo tiene al sujeto.
     *
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si su posición no es "1"
     */
    public ArbolGenealogico(Persona sujeto) {
        if (sujeto == null) {
            throw new ArbolInvalidoException("El sujeto no puede ser nulo.");
        }
        sujeto.validar();
        if (!Posicion.esSujeto(sujeto.getPosicion())) {
            throw new ArbolInvalidoException("El sujeto debe estar en la posición 1 (recibió la "
                    + sujeto.getPosicion() + ").");
        }
        this.raiz = new Nodo(sujeto);
        this.cantidad = 1;
    }

    /** @return el sujeto (la persona de la posición "1") */
    public Persona sujeto() {
        return raiz.persona;
    }

    /** @return cuántos miembros tiene el árbol (el sujeto cuenta; un árbol nuevo tiene 1) */
    public int tamano() {
        return cantidad;
    }

    /**
     * @return la persona de esa posición, o null si no existe en el árbol o si la posición no es válida
     */
    public Persona buscar(String posicion) {
        Nodo n = buscarNodo(posicion);
        return n == null ? null : n.persona;
    }

    /** @return true si hay alguien en esa posición (false también si la posición no es válida) */
    public boolean contiene(String posicion) {
        return buscarNodo(posicion) != null;
    }

    // ---------------------------------------------------------------------------------------------
    // Búsqueda: se sigue el CAMINO de la posición desde la raíz (no hay Map ni Set).
    // ---------------------------------------------------------------------------------------------

    /** @return el nodo de esa posición o null si no existe o la posición no es válida. */
    private Nodo buscarNodo(String posicion) {
        if (Posicion.esAhnentafel(posicion)) {
            return buscarAscendiente(Integer.parseInt(posicion));
        }
        if (Posicion.esDAboville(posicion)) {
            return buscarDescendiente(posicion);
        }
        return null;
    }

    /**
     * Ahnentafel: los bits de n DESPUÉS del primer 1 son el camino (0 = padre, 1 = madre).
     * Ej.: 5 = 101 -> bits "01": padre, luego madre.
     */
    private Nodo buscarAscendiente(int n) {
        Nodo actual = raiz;
        for (int bit = 30 - Integer.numberOfLeadingZeros(n); bit >= 0 && actual != null; bit--) {
            actual = ((n >> bit) & 1) == 0 ? actual.padre : actual.madre;
        }
        return actual;
    }

    /** D'Aboville: cada número después del "1" indica a qué hijo bajar. Ej.: 1.2.3 -> hijo 2, luego su hijo 3. */
    private Nodo buscarDescendiente(String posicion) {
        String[] partes = posicion.split("\\.");
        Nodo actual = raiz;
        for (int i = 1; i < partes.length && actual != null; i++) {
            actual = hijoConOrden(actual, Integer.parseInt(partes[i]));
        }
        return actual;
    }

    private static Nodo hijoConOrden(Nodo nodo, int orden) {
        for (Nodo hijo : nodo.hijos) {
            if (hijo.orden() == orden) {
                return hijo;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Agregar
    // ---------------------------------------------------------------------------------------------

    /**
     * Agrega una persona en la posición que ella misma trae (la usa el constructor del árbol a partir del texto).
     * El orden de llamada importa: primero debe existir la persona de la que depende (ver REGLAS DEL ÁRBOL).
     *
     * @return la misma persona
     * @throws IllegalArgumentException si la persona no es válida (Persona.validar)
     * @throws ArbolInvalidoException si la posición ya existe (o es "1"), si falta la persona de la que depende,
     *         o si los años de nacimiento no cumplen las reglas
     */
    public Persona agregar(Persona persona) {
        if (persona == null) {
            throw new ArbolInvalidoException("La persona no puede ser nula.");
        }
        persona.validar();
        String pos = persona.getPosicion();

        if (Posicion.esSujeto(pos)) {
            throw new ArbolInvalidoException("La posición 1 (el sujeto) está repetida.");
        }
        if (buscarNodo(pos) != null) {
            throw new ArbolInvalidoException("La posición " + pos + " está repetida.");
        }

        if (Posicion.esAscendiente(pos)) {
            // El ascendiente n cuelga de su hijo n / 2 y debe haber nacido ANTES que él.
            String posHijo = Posicion.posicionDelHijoEnLinea(pos);
            Nodo hijo = buscarNodo(posHijo);
            if (hijo == null) {
                throw new ArbolInvalidoException("No se puede agregar la posición " + pos
                        + ": primero debe existir su hijo en la posición " + posHijo + ".");
            }
            if (persona.getNacimiento() >= hijo.persona.getNacimiento()) {
                throw new ArbolInvalidoException("La posición " + pos + " (nació en " + persona.getNacimiento()
                        + ") debe haber nacido antes que su hijo de la posición " + posHijo
                        + " (nació en " + hijo.persona.getNacimiento() + ").");
            }
            Nodo nuevo = new Nodo(persona);
            if (Integer.parseInt(pos) % 2 == 0) {
                hijo.padre = nuevo;     // número par -> padre
            } else {
                hijo.madre = nuevo;     // número impar -> madre
            }
        } else {
            // El descendiente cuelga de su progenitor y debe haber nacido DESPUÉS que él.
            String posProgenitor = Posicion.posicionDelProgenitor(pos);
            Nodo progenitor = buscarNodo(posProgenitor);
            if (progenitor == null) {
                throw new ArbolInvalidoException("No se puede agregar la posición " + pos
                        + ": primero debe existir su progenitor en la posición " + posProgenitor + ".");
            }
            if (persona.getNacimiento() <= progenitor.persona.getNacimiento()) {
                throw new ArbolInvalidoException("La posición " + pos + " (nació en " + persona.getNacimiento()
                        + ") debe haber nacido después que su progenitor de la posición " + posProgenitor
                        + " (nació en " + progenitor.persona.getNacimiento() + ").");
            }
            insertarOrdenado(progenitor.hijos, new Nodo(persona));
        }
        cantidad++;
        return persona;
    }

    /** Inserta el nodo en la lista de hijos manteniéndola ordenada por número de orden. */
    private static void insertarOrdenado(List<Nodo> hijos, Nodo nuevo) {
        int i = hijos.size();
        while (i > 0 && hijos.get(i - 1).orden() > nuevo.orden()) {
            i--;
        }
        hijos.add(i, nuevo);
    }

    /**
     * Agrega el PADRE de la persona de esa posición, en la posición 2n (ver Posicion.posicionDelPadre).
     * Solo se pueden agregar padres al sujeto y a sus ascendientes.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un ascendiente, si ese padre
     *         ya existe o si no cumple las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarPadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        return agregarProgenitor(posicion, true, nombre, nacimiento, fallecimiento);
    }

    /**
     * Igual que agregarPadre, pero agrega la MADRE (posición 2n + 1).
     */
    public Persona agregarMadre(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        return agregarProgenitor(posicion, false, nombre, nacimiento, fallecimiento);
    }

    /** Lógica común de agregarPadre y agregarMadre. */
    private Persona agregarProgenitor(String posicion, boolean esPadre, String nombre, int nacimiento,
                                      Integer fallecimiento) {
        if (buscarNodo(posicion) == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe en el árbol.");
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esAscendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar padres y madres al sujeto y a sus "
                    + "ascendientes (la posición " + posicion + " es un descendiente).");
        }
        String posNueva = esPadre ? Posicion.posicionDelPadre(posicion) : Posicion.posicionDeLaMadre(posicion);
        if (!Posicion.esAhnentafel(posNueva)) {
            throw new ArbolInvalidoException("No se pueden agregar más generaciones de ascendientes "
                    + "(el máximo es la posición " + Posicion.MAX_AHNENTAFEL + ").");
        }
        if (buscarNodo(posNueva) != null) {
            throw new ArbolInvalidoException("La posición " + posicion + " ya tiene "
                    + (esPadre ? "padre" : "madre") + " (posición " + posNueva + ").");
        }
        return agregar(new Persona(posNueva, nombre, nacimiento, fallecimiento));
    }

    /**
     * Agrega un HIJO a la persona de esa posición. Solo se pueden agregar hijos al sujeto y a sus descendientes.
     * Su posición es la del progenitor + "." + el número siguiente al MAYOR número de hijo que ya exista
     * (o 1 si todavía no tiene hijos). Ej.: si "1" tiene los hijos 1.1 y 1.3, el nuevo es 1.4.
     *
     * @return la persona creada
     * @throws ArbolInvalidoException si la posición no existe, si no es el sujeto ni un descendiente, o si no cumple
     *         las reglas del árbol
     * @throws IllegalArgumentException si los datos de la persona no son válidos
     */
    public Persona agregarHijo(String posicion, String nombre, int nacimiento, Integer fallecimiento) {
        Nodo progenitor = buscarNodo(posicion);
        if (progenitor == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe en el árbol.");
        }
        if (!Posicion.esSujeto(posicion) && !Posicion.esDescendiente(posicion)) {
            throw new ArbolInvalidoException("Solo se pueden agregar hijos al sujeto y a sus descendientes "
                    + "(la posición " + posicion + " es un ascendiente).");
        }
        // La lista de hijos está ordenada: el último tiene el mayor número de orden.
        int siguiente = progenitor.hijos.isEmpty()
                ? 1
                : progenitor.hijos.get(progenitor.hijos.size() - 1).orden() + 1;
        if (siguiente > Posicion.MAX_ORDEN) {
            throw new ArbolInvalidoException("La posición " + posicion + " ya no admite más hijos "
                    + "(el máximo es " + Posicion.MAX_ORDEN + ").");
        }
        return agregar(new Persona(posicion + "." + siguiente, nombre, nacimiento, fallecimiento));
    }

    // ---------------------------------------------------------------------------------------------
    // Eliminar
    // ---------------------------------------------------------------------------------------------

    /**
     * Elimina a la persona de esa posición y TODO lo que depende de ella:
     *   - si es un ascendiente: ella y todos sus propios ascendientes (sus padres, abuelos...);
     *   - si es un descendiente: ella y todos sus propios descendientes (hijos, nietos...).
     * Las demás posiciones NO cambian de número.
     *
     * @return cuántas personas se eliminaron (ella incluida)
     * @throws ArbolInvalidoException si la posición es "1" (el sujeto no se puede eliminar) o si no existe
     */
    public int eliminar(String posicion) {
        if (Posicion.esSujeto(posicion)) {
            throw new ArbolInvalidoException("El sujeto (posición 1) no se puede eliminar.");
        }
        Nodo nodo = buscarNodo(posicion);
        if (nodo == null) {
            throw new ArbolInvalidoException("La posición " + posicion + " no existe en el árbol.");
        }

        int eliminados;
        if (Posicion.esAscendiente(posicion)) {
            // Se desconecta del hijo (n / 2): al soltar el nodo se va con todo su subárbol de ascendientes.
            Nodo hijo = buscarNodo(Posicion.posicionDelHijoEnLinea(posicion));
            if (Integer.parseInt(posicion) % 2 == 0) {
                hijo.padre = null;
            } else {
                hijo.madre = null;
            }
            eliminados = contarAscendientes(nodo);
        } else {
            // Se quita de la lista de hijos del progenitor: se va con todos sus descendientes.
            Nodo progenitor = buscarNodo(Posicion.posicionDelProgenitor(posicion));
            progenitor.hijos.remove(nodo);
            eliminados = contarDescendientes(nodo);
        }
        cantidad -= eliminados;
        return eliminados;
    }

    private static int contarAscendientes(Nodo nodo) {
        if (nodo == null) {
            return 0;
        }
        return 1 + contarAscendientes(nodo.padre) + contarAscendientes(nodo.madre);
    }

    private static int contarDescendientes(Nodo nodo) {
        int total = 1;
        for (Nodo hijo : nodo.hijos) {
            total += contarDescendientes(hijo);
        }
        return total;
    }

    // ---------------------------------------------------------------------------------------------
    // Recorridos
    // ---------------------------------------------------------------------------------------------

    /**
     * @return todos los miembros en este orden: el sujeto; luego los ascendientes por número Ahnentafel
     *         ascendente (2, 3, 4, 5...); luego los descendientes en PREORDEN (cada persona y después sus hijos:
     *         1.1, 1.1.1, 1.1.2, 1.2, ...). La lista es una copia nueva: modificarla no cambia el árbol.
     *         Este orden garantiza que cada persona aparece DESPUÉS de la que necesita (útil para guardar y cargar).
     */
    public List<Persona> miembros() {
        List<Persona> lista = new ArrayList<>();
        lista.add(raiz.persona);
        lista.addAll(ascendientes());
        lista.addAll(descendientes());
        return lista;
    }

    /** @return solo los ascendientes, por número Ahnentafel ascendente (sin el sujeto) */
    public List<Persona> ascendientes() {
        // Recorrido por NIVELES (BFS): como el nivel g contiene los números 2^g .. 2^(g+1)-1 y en cada
        // nodo se visita primero el padre (2n) y luego la madre (2n+1), sale en orden Ahnentafel.
        List<Persona> lista = new ArrayList<>();
        Queue<Nodo> cola = new ArrayDeque<>();
        encolar(cola, raiz.padre);
        encolar(cola, raiz.madre);
        while (!cola.isEmpty()) {
            Nodo n = cola.remove();
            lista.add(n.persona);
            encolar(cola, n.padre);
            encolar(cola, n.madre);
        }
        return lista;
    }

    private static void encolar(Queue<Nodo> cola, Nodo nodo) {
        if (nodo != null) {
            cola.add(nodo);
        }
    }

    /** @return solo los descendientes, en preorden (sin el sujeto) */
    public List<Persona> descendientes() {
        List<Persona> lista = new ArrayList<>();
        for (Nodo hijo : raiz.hijos) {
            preorden(hijo, lista);
        }
        return lista;
    }

    /** Preorden: primero la persona y después cada uno de sus hijos (ya ordenados por número). */
    private static void preorden(Nodo nodo, List<Persona> lista) {
        lista.add(nodo.persona);
        for (Nodo hijo : nodo.hijos) {
            preorden(hijo, lista);
        }
    }
}