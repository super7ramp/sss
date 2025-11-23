(ns sss
  (:require [clojure.pprint :refer [pprint]]))

(defn- propagate
  "Propagates a literal in a SAT problem, to simplify the problem."
  [literal problem]
  (->> problem
       (into [] (comp (remove (fn [clause] (some #(== literal %) clause)))
                      (map (fn [clause] (remove #(== (- literal) %) clause)))
                      (halt-when empty? (fn [_ empty-clause] [empty-clause]))))
       (sort-by count)))

(defn solve
  "Solves a SAT problem represented as a list of clauses."
  [problem]
  (cond (empty? problem) [[]]
        (empty? (first problem)) []
        :else (let [[[literal]] problem]
                (concat (->> (propagate literal problem)
                             solve
                             (map #(cons literal %)))
                        (->> (propagate (- literal) problem)
                             solve
                             (map #(cons (- literal) %)))))))

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
                  (let [var (abs literal)
                        digit (mod (dec var) 9)
                        col (mod (quot (dec var) 9) 9)
                        row (quot (dec var) (* 9 9))]
                    (assoc-in grid [row col] (inc digit)))
                  grid))
              empty-grid
              assignment))))

(defn sudoku
  "Solves a sudoku puzzle represented as a 9x9 grid with 0 for empty cells."
  [grid]
  (let [clauses (sudoku-grid->clauses grid)]
    (->> (solve clauses)
         (map assignment->sudoku-grid))))

(defn -main []
  (println "Example: Trivial clauses")
  (println "Input: (1 or 2) and (-2 or 3)")
  (println (time (solve [[1 2] [-2 3]])))

  (println "Example: A sudoku problem")
  (println "Input:")
  (let [grid [[0 2 6 0 0 0 8 1 0]
              [3 0 0 7 0 8 0 0 6]
              [4 0 0 0 5 0 0 0 7]
              [0 5 0 1 0 7 0 9 0]
              [0 0 3 9 0 5 1 0 0]
              [0 4 0 3 0 2 0 5 0]
              [1 0 0 0 3 0 0 0 2]
              [5 0 0 2 0 4 0 0 9]
              [0 3 8 0 0 0 4 6 0]]]
    (pprint grid)
    (println "Solutions:")
    (pprint (time (sudoku grid)))))
