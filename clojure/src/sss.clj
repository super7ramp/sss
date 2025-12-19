(ns sss
  (:require [clojure.pprint :as pp :refer [pprint]]))

(defn- propagate
  "Propagates a unit clause (a literal), to simplify the problem."
  [literal problem]
  (->> problem
       (into [] (comp (remove (fn [clause] (some #(== literal %) clause)))
                      (map (fn [clause] (remove #(== (- literal) %) clause)))
                      (halt-when empty? (fn [_ empty-clause] [empty-clause]))))
       (sort-by count)))

(defprotocol Solver
  "SAT Solver."
  (solve [this problem]
    "Solves a SAT problem, represented as a conjunction (= 'and') of clauses.
    Clauses are disjunctions (= 'or') of literals, represented by integers.
    E.g., `[[1 -2] [2 3]]` represents (1 or (not 2)) and (2 or 3)."))

(def DefaultSolver
  "Default implementation of Solver. Recursive."
  (reify Solver
    (solve [this problem]
      (cond (empty? problem) [[]]
            (empty? (first problem)) []
            :else (let [literal (ffirst problem), negated-literal (- literal)]
                    (concat (->> (propagate literal problem)
                                 (solve this)
                                 (map #(cons literal %)))
                            (->> (propagate negated-literal problem)
                                 (solve this)
                                 (map #(cons negated-literal %)))))))))

(def IterativeSolver
  "Iterative implementation of Solver. Mimics recursion using a stack allocated on the heap.
   Prevents stack overflow for large problems."
  (reify Solver
    (solve [_this problem]
      (loop [stack (list [problem '()]), assignments []]
        (if (empty? stack)
          assignments
          (let [[clauses assignment] (first stack), stack (rest stack)]
            (cond
              (empty? clauses) (recur stack (conj assignments assignment))
              (empty? (first clauses)) (recur stack assignments)
              :else (let [literal (ffirst clauses)
                          propagation (propagate literal clauses)
                          negated-literal (- literal)
                          negated-propagation (propagate negated-literal clauses)
                          stack (-> stack
                                    (conj [negated-propagation (cons negated-literal assignment)])
                                    (conj [propagation (cons literal assignment)]))]
                      (recur stack assignments)))))))))

(defprotocol Solvable
  "A problem that can be solved with a Solver."
  (solutions [this] [this solver]))

; clause helpers

(defn at-most-one [literals]
  (for [i (range (count literals))
        j (range (inc i) (count literals))]
    [(- (nth literals i)) (- (nth literals j))]))

(defn exactly-one [literals]
  (into [literals] (at-most-one literals)))

; sudoku example

(defn- sudoku-var-num [row col digit]
  (+ (* row 9 9) (* col 9) digit))

(defn- sudoku-grid->clauses [grid]
  (let [rows (range 9), cols (range 9), digits (range 1 10)]
    (distinct
      (concat
        ; 1. No row contains dupe
        (mapcat exactly-one (for [row rows, digit digits]
                              (for [col cols]
                                (sudoku-var-num row col digit))))
        ; 2. No column contains dupe
        (mapcat exactly-one (for [col cols, digit digits]
                              (for [row rows]
                                (sudoku-var-num row col digit))))
        ; 3. No 3x3 box contains dupe
        (mapcat exactly-one (for [box-row (range 3), box-col (range 3), digit digits]
                              (for [i (range 3), j (range 3)]
                                (sudoku-var-num (+ (* box-row 3) i)
                                                (+ (* box-col 3) j)
                                                digit))))
        ; 4. No cell contains dupe
        (mapcat exactly-one (for [row rows, col cols]
                              (for [digit digits]
                                (sudoku-var-num row col digit))))
        ; 5. Initial values
        (for [row rows
              col cols
              :let [digit (nth (nth grid row) col)]
              :when (pos? digit)]
          [(sudoku-var-num row col digit)])))))

(defn- assignment->sudoku-grid [assignment]
  (when (not-empty assignment)
    (let [empty-grid (vec (repeat 9 (vec (repeat 9 0))))]
      (reduce (fn [grid literal]
                (if (pos? literal)
                  (let [digit (inc (mod (dec literal) 9))
                        col (mod (quot (dec literal) 9) 9)
                        row (quot (dec literal) (* 9 9))]
                    (assoc-in grid [row col] digit))
                  grid))
              empty-grid
              assignment))))

(defrecord Sudoku [grid clauses]
  Solvable
  (solutions [this]
    (solutions this DefaultSolver))
  (solutions [_this solver]
    (->> (solve solver clauses)
         (map assignment->sudoku-grid))))

(defmethod pp/simple-dispatch Sudoku
  [sudoku] (pprint (:grid sudoku)))

(defn make-sudoku
  "Creates a Sudoku instance from a 9x9 grid, with 0 for empty cells."
  [grid]
  (let [clauses (sudoku-grid->clauses grid)]
    (Sudoku. grid clauses)))

(defn -main []
  (println "Example: Trivial clauses")
  (println "Input: (1 or 2) and (-2 or 3)")
  (println (time (solve DefaultSolver [[1 2] [-2 3]])))

  (println "Example: A sudoku problem")
  (println "Input:")
  (let [sudoku (make-sudoku [[0 2 6 0 0 0 8 1 0]
                             [3 0 0 7 0 8 0 0 6]
                             [4 0 0 0 5 0 0 0 7]
                             [0 5 0 1 0 7 0 9 0]
                             [0 0 3 9 0 5 1 0 0]
                             [0 4 0 3 0 2 0 5 0]
                             [1 0 0 0 3 0 0 0 2]
                             [5 0 0 2 0 4 0 0 9]
                             [0 3 8 0 0 0 4 6 0]])]
    (pprint sudoku)

    (println "Solutions (using recursive solver):")
    (dotimes [run 3]
      (println "Run #" (inc run) ":")
      (pprint (time (solutions sudoku))))

    (println "Solutions (using iterative solver):")
    (dotimes [run 3]
      (println "Run #" (inc run) ":")
      (pprint (time (solutions sudoku IterativeSolver))))))
