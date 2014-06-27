(ns chess-clojure.core
  (:gen-class)
  (:require clojure.pprint) 
  (:use [clojure.string :only (split-lines join)])
  (:use [clojure.pprint :only (pprint)])

  )


;; Create a chess engine in Clojure
;;
;; data types:
;; -----------
;; pieces are letters rnbqkpRNBQKP  "r" "n" etc
;; location is a pair of numbers. [0 0] to [7 7]
;; board is a map of locations to pieces {[0 0] "R" [1 0] "N" }
;; moves are pairs of locations  [[0 0] [0 1]] 
;;   meaning move the piece at [0 0] to [0 1]
;; colors are :black and :white


;; Goals:
;; For a given board position generate all (legal) moves
;; Then create an evaluation function and create a chess engine
;; using monte carlo or min-max...
;;
;;(println starting-board)
;;
(def starting-board
  "Horizontal rows are ranks. Vertical rows are files"
  (clojure.string/join "\n" 
                       ["rnbqkbnr"
                        "pppppppp"
                        "Pb  P  P"
                        "R"
                        "p B"
                        " p    p"        
                        "PPPPPPPP"
                        "RNBQKBNR"] ))


(defn valid-location?
  [[x y]]
  (and (< x 8) (> x -1) (< y 8) (> y -1))
  [x y]
  (and (< x 8) (> x -1) (< y 8) (> y -1)))

(defn black-piece?[x]
  (contains? #{"r" "n""b" "q" "k" "p"} x)
  )

(defn white-piece?[x]
  (contains? #{"R" "N""B" "Q" "K" "P"} x)
  )


(defn occupied?[board location]
  (contains? board location) 
  )

(defn num->alpha[x]
  (assert number? x)
  (assert (> x -1))
  (assert (< x 8))
  (get "abcdefgh" x)) 

(defn move->algebraic-notation[board [[x1 y1] [x2 y2]]]
  (str (get board [x1 y1])
       (num->alpha x1)
       (inc y1)
       (if (occupied? board [x2 y2])
         "x")
       (num->alpha x2)
       (inc y2) 
       ) 
  )
;;; Write a program that will generate legal moves.
;; coordinat


(defn string->board[string]
  (assert (string? string))
  (let [lines (vec (reverse (clojure.string/split-lines string)))
        ]
    (into (sorted-map) 
          (remove 
            (fn [x]
              ;;      (println "second x" (second x))
              (or (nil? (second x))
                  (= "" (second x))
                  (= " " (second x ))))
            (for [x (range 8) y (range 8)]
              [[x y] 
               (str (get (get lines y) x))]
              )))))

;; 
;; [0 0] "R"
;; [1 0] "N"
;; ....
;; [0 7] "r"
;;


(comment
  "Each location of the chessboard is identified by a unique coordinate pair—a letter and a number. The vertical column of squares (called files) from White's left (the queenside) to his right (the kingside) are labeled a through h. The horizontal rows of squares (called ranks) are numbered 1 to 8 starting from White's side of the board. Thus each square has a unique identification of file letter followed by rank number. (For example, White's king starts the game on square e1; Black's knight on b8 can move to open squares a6 or c6.)")


(comment
  "tests here"
  (println starting-board)
  )

(defn get-color[piece]
  (cond (black-piece? piece)
        :black
        (white-piece? piece)
        :white
        ))

(defn is-piece?[x]
  (or (black-piece? x) (white-piece? x)))

(defn is-pawn?[x]
  (contains? #{"P" "p"} x)
  )

(defn occupied-by-enemy? [board location color]
  (comment
    (println "occupied-by-enemy?")
    (println board)
    (println  location)
    (println color)
    )
  (and (occupied? board location)
       (not (= color (get-color (get board location))))))

(defn off-board? [[x y]]
  (or (< x  0) (> x  7) (< y 0) (> y 7)))

(defn is-bishop?[board loc]
  (#{"b" "B"} (get board loc)))

(defn is-board?[x]
  (map? x))
(do
(def all-locations
  "A lazy seq of all chess locations. -> ( [0 0] [0 1] ....[7 6] [7 7 ])"
   (for [x (range 7 -1 -1 ) y (range 0 8)]
     [y x]))

(defn board->string[board]
  (assert is-board? board)
  (reduce (fn[accum location]
            ;;(println location) (println "accum is " accum)
            (str accum
                             (if (= 0 (first location))
                                      "\n")
                                   (get board location " ")
                 )) "" all-locations))
(println (board->string (string->board starting-board)))
  (println all-locations)
  )
(defn on-board?[loc] (not (off-board? loc)))

(defn bishop-non-captures[board location]
  (assert (is-bishop? board location))
  (assert (is-board? board))
  (assert (valid-location? location))
  (let [[x y] location]
    (into []  ;
          (map (fn[loc2] [location loc2])
               (apply concat 
                      (for [op1  [+ -] op2 [+ -]]  ;; to generate 4 diagonals
                        (take-while (fn[loc]
                                      (and
                                        (on-board? loc)
                                        (not (occupied? board loc)))) 
                                    (for [z (drop 1(range))]  [(op1 x z) (op2 y z)])
                                    )
                        ))))))

(defn bishop-captures[board location]
  "return list of captures that the bishop at location can make"
  ;; (println board location)
  (assert (is-bishop? board location))
  (assert (is-board? board))
  (assert (valid-location? location))
  (let [color (get-color (get board location))
        [x y] location
        ]
    (remove nil?
            (for [op1  [+ -] op2 [+ -]]  ;; to generate 4 diagonals
              (loop [locs (for [z (drop 1(range))] 
                            [(op1 x z) (op2 y z)])
                     ]
                (cond (off-board? (first locs))
                      nil
                      (occupied-by-enemy? board (first locs) color)
                      [ location (first locs) ]
                      (occupied? board (first locs))
                      nil
                      true
                      (recur  (next locs))))))))

(comment
  (pprint (bishop-captures (string->board 
                             (join "\n" ["r N"
                                         " B "
                                         "   "] 
                                   )) [1 1]))
  )

;;(pprint (bishop-non-captures (string->board " b\n  ")[1 1]))
(defn bishop-moves[board location]
  (println "bishop-moves")
  ;; (println board location)
  ;; juxt ??
  (concat (bishop-non-captures board location)
          (bishop-captures board location)))

(defn pawn-captures[board [x y :as location]]
  "return valid capture moves for the pawn at the location on the board"
  (let [color (get-color (get board location))
        op (if (= :white color) inc dec) 
        ]
    (keep 
      (fn[dec-or-inc]
        (if (occupied-by-enemy? board [(dec-or-inc x) (op y)] color)
          [[x y] [(dec-or-inc x) (op y)]]

          )) [inc dec]) 
    ))


(defn pawn-non-capturing-moves[board [x y :as location] ]
  (assert (is-pawn? (get board location)))
  (let [piece (get board location)
        op (if (white-piece? piece)
             +
             -) 
        on-second (if (white-piece? piece)
                    (= y 2)
                    (= y 6))
        ]

    ;; (println "entering pawn-moves, board is" board)
    ;; (println "entering pawn-moves, location is" location)
    ;;  (case piece "P"
    ;; (cond (> 1 x)
    (let [ extra 
          (if (and on-second
                   (not (occupied? board [ x (op y 1 ) ]))
                   (not (occupied? board [ x (op y 2) ])))
            [   [location [x (op y 2) ]] ])
          ]
      (concat extra 
              (if-not (occupied? board [x (op y 1) ])
                [   [location [x (op y 1) ]] ]
                []
                )))
    ))

(defn pawn-moves[board [x y :as location] ]
  (assert (is-pawn? (get board location)))
  (concat (pawn-non-capturing-moves board location)
          (pawn-captures board location)
          ))

(defn is-color?[piece white-or-black]
  (case white-or-black
    :white
    (white-piece? piece)
    :black
    (black-piece? piece)
    ))

(defn moves[board white-or-black]
  (remove nil? (mapcat (fn[[loc piece :as z]]
                         (case piece
                           ("p" "P")
                           (pawn-moves board loc) 
                           ("b" "B")
                           (bishop-moves board loc)
                           nil 
                           )) (filter (fn[[_ piece]]
                                        (is-color?  piece white-or-black)) board)
                       ))) 


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

;; tests
;; (pprint starting-board)
;;(pprint (map identity (string->board starting-board)))
;; (pprint (string->board starting-board))
;;
;;(pprint (moves (string->board starting-board) :white))


(defn test1[]
  (let [board (string->board starting-board)]
    (println starting-board)
    (map #(move->algebraic-notation board %) 
         (moves (string->board starting-board) :black))))

(pprint (test1))
(comment
  (let [board (string->board starting-board)] 
    (println starting-board)
    (pprint (filter #(is-color? (second %) :white) board)))
  )

;;(println "hi2345  ")
