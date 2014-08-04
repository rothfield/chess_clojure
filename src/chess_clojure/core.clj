(ns chess-clojure.core
  (:gen-class)
  (:require clojure.pprint) 
  (:use [clojure.test :only [is]])
  (:use [clojure.string :only (split-lines join)])
  (:use [clojure.pprint :only (pprint)])
  )
;;; TODO: create position protocol ??
;;;
;;; Key abstractions: position piece move location
(comment
  ;; to use in repl:
  ;; cpr runs current file in vim
  ;; cp% runs current form. vim-fireplace
  ;; cpp runs form under cursor
  (set! *warn-on-reflection* true)
  (use 'chess_clojure.core :reload) (ns chess_clojure.core)
  (use 'clojure.stacktrace)
  (print-stack-trace *e)
  (use 'doremi_script.test-helper :reload)  ;; to reload the grammar
  (print-stack-trace *e)
  (pst)
  )

;; Create a chess engine in Clojure
;;
;; data types:
;; -----------
;; pieces are strings (letters) rnbqkpRNBQKP  :r :n etc
;; location is a pair of numbers. [0 0] to [7 7]
;; board is a map of locations to pieces {[0 0] :r [1 0] :N }
;; [0 0] is white's queen rook
;; moves are pairs of locations  [[0 0] [0 1]] 
;;   meaning move the piece at [0 0] to [0 1]
;;   It is a capture if there is an enemy piece at the second location
;; colors are :black and :white
;;
;; Functions to read in a position: string->position 
;; and to output a position: position->string


;; Goals:
;; For a given position position generate all (legal) moves
;; Then create an evaluation function and create a chess engine
;; using monte carlo or min-max...
;;
;;

(def debug false)

(declare moves locations-under-attack string->position string->position-and-print)

(declare white-to-move? black-to-move?)

(defn my-is-a?[my-type obj]
  (and (map? obj)
       (= my-type (:type obj))))

(defn piece-at-location[position location]
  (get (:piece-locations position) location))

(defn is-position?[x]
  (= :position (:type x)))

(defn is-color?[x]
  (get #{:white :black} x))
;; (println starting-position)
;; (castling-available-white-queenside? starting-position)
(def starting-position-string
   (join "\n" [
              "rnbqkbnr"
              "pppppppp"
              "        "
              "        "
              "        "
              "        "
              "PPPPPPPP"
              "RNBQKBNR\n"]))

;;
(defn valid-location?
  [[x y]]
  (and (< x 8) (> x -1) (< y 8) (> y -1)))

(defn black-piece?[x]
  (contains? #{:r :n :b :q :k :p} x)
  )

(defn white-piece?[x]
  (contains? #{:R :N :B :Q :K :P} x)
  )

(defn occupied?[position location]
  (contains? (:piece-locations position) location) 
  )

(defn unoccupied?[position location]
  (not (occupied? position location)))

(defn num->alpha[x]
  (assert number? x)
  (assert (> x -1))
  (assert (< x 8))
  (get "abcdefgh" x)) 


(defn move->algebraic-notation[position [[x1 y1] [x2 y2]]]
  ;; assert if occupied, must be enemy 
  (str (name (piece-at-location position [x1 y1]))
       (num->alpha x1)
       (inc y1)
       (if (occupied? position [x2 y2])
         "x")
       (num->alpha x2)
       (inc y2) 
       ) 
  )

(defn is-knight?[x]
  (contains? #{:n :N} x)
  )
(defn is-king?[x]
  (contains? #{:k :K} x)
  )

(defn is-pawn?[x]
  (contains? #{:P :p} x)
  )

(def piece-color 
  {:r :black :n :black :b :black :q :black :k :black
   :p :black
   :R :white :N :white :B :white :Q :white :K :white
   :P :white})

(defn friendly-square? [position location]
  (and (occupied? position location)
       (= (:player-to-move position)
          (piece-color (piece-at-location position location)))))

(defn occupied-by-enemy? [position location]
  (and (occupied? position location)
       (not (= (:player-to-move position)
               (piece-color (piece-at-location position location))))))

(defn off-board? [[x y]]
  (or (< x  0) (> x  7) (< y 0) (> y 7)))


;;  (map? x))

(def locations-helper
  "A lazy seq of all chess locations. -> ( [0 0] [0 1] ....[7 6] [7 7 ])"
  (for [
        y (range 7 -1 -1 )
        x (range 0 8)
        ]
    [x y]))

;;[FEN
;;
;;(def fen-str "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
;; (println (position->string (fen->position fen-str)))
(defn fen->position[x]
  (let [ [ fen-str player-to-move castling-state ] (clojure.string/split x #" ")]
  ;; x is a fen string
(string->position  (-> fen-str  (.replaceAll "/" "\n")
  (.replaceAll "8" "        ")
  (.replaceAll "7" "       ")
  (.replaceAll "6" "      ")
  (.replaceAll "5" "     ")
  (.replaceAll "4" "    ")
  (.replaceAll "3" "   ")
  (.replaceAll "2" "  ")
     ) (get {"w" :white :b :black} player-to-move)     )))
;;[FEN "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"]

(declare position->string)  

(defn display-piece[piece]
  (if (nil? piece) " "
    (name piece)))

(defn display-to-move[position]
  (str (name (:player-to-move position)) " to move:\n\n"))

(defn position->string[position]
 ;; (println "position->string, positon: " position)
  ;;  (assert is-position? position)
  (str
    (display-to-move position)
    (reduce (fn[accum location]
              (str accum
                   (display-piece (piece-at-location position location))
                   (if (= 7 (first location))
                     "\n")
                   (if (and (= 7 (first location))
                            (not= 0 (second location)))
                     (str (dec (second location)) " ")))
              ) "7 " locations-helper)
    ;; "  abcdefgh" 
    "  01234567"
    "\n\n"
    ))

(defn on-board?[loc] (not (off-board? loc)))

(defn rook-right[x y z] [ (+ x z) y ])

(defn rook-left[x y z] [ (- x z) y ])

(defn rook-up[x y z] [ x (+ y z)])

(defn rook-down[x y z] [ x (- y z)])

(defn bishop-ne[x y z] [ (+ x z) (+ y z) ])

(defn bishop-nw[x y z] [ (- x z) (+ y z) ])

(defn bishop-se[x y z] [ (+ x z) (- y z)])

(defn bishop-sw[x y z] [ (- x z) (- y z)])

(defn inc2[x] (+ x 2))
(defn dec2[x] (- x 2))
(def white-pawn-attack-rules 
  [
   (fn[x y] [ (dec x) (inc y)])
   (fn[x y] [ (inc x) (inc y)])
   ])
(def black-pawn-attack-rules 
  [
   (fn[x y] [ (dec x) (dec y)])
   (fn[x y] [ (inc x) (dec y)])
   ])

(def pawn-attack-rules {:white white-pawn-attack-rules
                        :black black-pawn-attack-rules})
(def knight-rules 
  [
   (fn[x y] [ (inc x) (inc2 y)])
   (fn[x y] [ (inc x) (dec2 y)])
   (fn[x y] [ (inc2 x) (inc y)])
   (fn[x y] [ (inc2 x) (dec y)])
   (fn[x y] [ (dec x) (inc2 y)])
   (fn[x y] [ (dec x) (dec2 y)])
   (fn[x y] [ (dec2 x) (inc y)])
   (fn[x y] [ (dec2 x) (dec y)])
   ])

(defn king-ne[x y] [(inc x) (inc y)])
(defn king-nw[x y] [(dec x) (inc y)])
(defn king-se[x y] [(inc x) (dec y)])
(defn king-sw[x y] [(dec x) (dec y)])
(defn king-left[x y] [(dec x) y])
(defn king-right[x y] [(inc x) y])
(defn king-up[x y] [x (inc y)])
(defn king-down[x y] [x (dec y)])

(def king-rules  [king-ne king-nw king-se king-sw king-left king-right
                  king-up king-down
                  ])

(def rook-rules
  [rook-right rook-left rook-up rook-down])

(def bishop-rules
  [bishop-ne bishop-nw bishop-se bishop-sw])


(defn pieces-info[x]
  (case x
    (:r :R)  
    {:rules rook-rules
     :ranging true}
    (:n :N) {
             :rules knight-rules
             }
    (:b :B) {:rules 
             bishop-rules
             :ranging true}
    (:q :Q) {:rules
             (concat bishop-rules rook-rules)
             :ranging true }
    (:k :K) {
             :rules king-rules
             }
    (:p :P) {
             }
    )) 


(defn non-ranging-moves[position location rules]
  ;;; includes captures
  (map (fn[loc2] [location loc2])
       (remove (fn[loc2] (friendly-square? position loc2))
               (filter on-board? 
                       (map (fn[rule] (apply rule location))
                            rules))
               )))


(def is-ranging? #{:q :Q :b :B :R :r})

(defn ranging-moves-in-direction[position location direction-fn]
  ;; includes captures
  ;;;;
  ;; location is the location of the friendly piece in question 
  ;; direction-fn generate locations to test using a function applied to the range 1..infinity
  ;; Finished when 
  ;; 1. off board 
  ;; 2. piece found 
  ;; Returns set of zero or more moves found
  ;;
  (when debug 
    (println "entering ranging-moves-in-direction, direction-fn is" direction-fn))
  (let [[x y] location
        debug false
        ]
    (when debug
      (println x y)
      (println (position->string position))
      (println location))
    (loop [locs-to-test (for [z (drop 1(range))]  ;; lazy seq of locations to test
                          (direction-fn x y z))
           accum []
           ]
      (let [loc-to-test (first locs-to-test)]
        (when debug 
          (println "processing " loc-to-test))
        (cond 
          (off-board? loc-to-test)
          accum
          (occupied-by-enemy? position loc-to-test)
          (conj accum (vector location loc-to-test)) 
          (occupied? position loc-to-test)
          accum
          true  ;; location is empty
          (recur  (next locs-to-test) (conj accum (vector location loc-to-test)) )))
      )))



(defn piece-info-at-location[position location]
  (get pieces-info 
       (piece-at-location position location)))

(defn ranging-moves[position location rules]
  (assert (is-ranging? (piece-at-location position location)))
  ;; (assert (is-position? position))
  (assert (valid-location? location))
  (remove empty?
          (mapcat
            (partial ranging-moves-in-direction position location)
            rules 
            )))

(defn pawn-capturing-locations[position [x y :as location]]
  (let [
        op (if (= :white (:player-to-move position)) inc dec) 
        ]
    "return valid capture locations for the pawn at given location"
    (remove off-board?
            (keep 
              (fn[dec-or-inc]
                (if (unoccupied? position [(dec-or-inc x) (op y)])
                  [(dec-or-inc x) (op y)]

                  )) [inc dec]) 
            )))

(defn pawn-captures[position [x y :as location]]
  "return valid capture moves for the pawn at the location on the position"
  (let [color (piece-color (piece-at-location position location))
        op (if (= :white color) inc dec) 
        ]
    (keep 
      (fn[dec-or-inc]
        (if (occupied-by-enemy? position [(dec-or-inc x) (op y)])
          [[x y] [(dec-or-inc x) (op y)]]

          )) [inc dec]) 
    ))


(defn pawn-non-capturing-moves[position [x y :as location] ]
  (assert (is-pawn? (piece-at-location position location)))
  (when debug (println position location))
  (let [piece (piece-at-location position location)
        op (if (white-piece? piece)
             +
             -) 
        on-second (if (white-piece? piece)
                    (= y 2)
                    (= y 6))
        ]

    (let [ extra 
          (if (and on-second
                   (not (occupied? position [ x (op y 1 ) ]))
                   (not (occupied? position [ x (op y 2) ])))
            [   [location [x (op y 2) ]] ])
          ]
      (concat extra 
              (if-not (occupied? position [x (op y 1) ])
                [   [location [x (op y 1) ]] ]
                []
                )))
    ))

(defn pawn-moves[position [x y :as location] ]
  (assert (is-pawn? (piece-at-location position location)))

  (apply concat 
         ((juxt pawn-non-capturing-moves pawn-captures)
          position location)
         ))


(def opposite-color {:black :white :white :black})

(defn flip-position[position]
  (assoc position 
         :locations-under-attack []
         :player-to-move (opposite-color (:player-to-move position))))




(defn king-location[position]
  "TODO: don't use scan???"
  (let [piece (if (= :white (:player-to-move position)) 
                :K
                :k)]
    (some #(if (= piece (second %))
             (first %))
          (:piece-locations position))
    ))
;; (:king-moves (string->position-and-print :K :white)
;;         (king-moves (string->position-and-print :K :white))
;;
;;(test-king-cannot-move-to-a-location-under-attack)
(defn king-moves[position]
  (when false 
    (println "entering king-moves")
    (println "under attack" (:locations-under-attack position))
    )
  (remove (fn[move]
            (when debug
              (println "in fn, move=" move))
            (contains? (:locations-under-attack position)
                       (second move)))
          (non-ranging-moves position (king-location position)
                             king-rules) 
          ))

;;; why??
(def piece-letter #{"r" "R" "N" "n" "B" "b" "Q" "q" "K" "k" "P" "p"})

(defn string->position-base[string color]
  {:post  [(is-position? %)]
   :pre [(string? string)
         (is-color? color)] }
  (when debug (println "entering string->position"))
  (let [lines (vec (reverse (clojure.string/split-lines string)))
        ]
    { :type :position
     :identifier "start"  ;;; Not unique! put move here!!! [[5 1] [5 3]]
     :castling-availability #{ :K :Q :k :q }
     :player-to-move color
     :piece-locations
     (into (sorted-map) 
           (filter (fn[zzz] (not (nil? (second zzz))))
                   (for [x (range 8) y (range 8)
                         :let [ location [x y]
                               my-piece-str (str (get (get lines y) x)) 
                               ;;  _ (println "my-piece-str is" my-piece-str)
                               piece 
                               (if (get piece-letter my-piece-str)
                                 (keyword my-piece-str))

                               ]
                         ]
                     [location piece]  ;; map entry
                     )))
     }))

(declare update-position)
(defn string->position[string color]
  {:post  [(is-position? %)]
   :pre [(string? string)
         (is-color? color)] }
  (update-position (string->position-base string color)))
;;; (println (position->string (string->position "K k r R" :white)))
(defn test-fen->position[]
   (let [fen-str "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"]
   (-> fen-str fen->position position->string println) 
  ))
;; (println (test-fen->position))

;; (insufficient-material  (string->position-and-print "k K" :white))
(defn insufficient-material[position]
  (let [piece-set (into #{}
                        (map second (:piece-locations position)))]
    (cond (= 2 (count piece-set))
          true
          true
          false)))


(defn update-status[position]
  (assoc position :status
         (cond
           (and (:in-check position)
                (= 0 (count (moves position))))
           :checkmate 
           (zero? (count (:moves position)))
           :stalemate
           (insufficient-material position)
           :draw-by-insufficient-material
           true
           :active
           )
         ))

(defn update-in-check[position]
  (assoc position :in-check
         (contains? (:locations-under-attack position)
                    (king-location position))
         ))


(defn update-under-attack[position]
  (assoc position :locations-under-attack
         (locations-under-attack position)))

(defn update-king-moves[position]
  (assoc position :king-moves (king-moves position)))


(defn update-algebraic-moves[position]
  (assoc position :algebraic-moves 
         (map (partial move->algebraic-notation position)
              (:moves position))))

(defn update-moves[position]
  (assoc position :moves (moves position))
  )


(defn update-position[position]
  (-> position 

      update-under-attack
      update-king-moves
      update-in-check
      update-moves
      update-status
      update-algebraic-moves))

(defn location-at-eigth-rank?[position location]
  (= (get { :white 7 :black 0} (:player-to-move position))
     (second location)))
(defn choose-promoted-piece[color]
  (if (= color :white)
    :Q
    :q))
;; (rand-nth [:R :N :B :Q])
;; (rand-nth [:r :n :b :q])))


(defn update-castling-availability[position move]
  (if (empty? (:castling-availability position))
    position
    ;; else 
    (let [availability (:castling-availability position)]

     ;; (println availability)
      (assoc position :castling-availability
             (cond 
               (or 
                 (= (first move) [0 0]) ;; white queens rook
                 (= (second move) [0 0]))
               (do
                 (disj availability :Q)
                 )
               (or 
                 (= (first move) [7 0])  ;; white kings rook
                 (= (second move) [7 0]))
               (disj availability :K)
               (or 
                 (= (first move) [0 7]) ;; black queens rook
                 (= (second move) [0 7]))
               (disj availability :q)
               (or 
                 (= (first move) [7 7])  ;; black kings rook
                 (= (second move) [7 7]))
               (disj availability :k)   ;; white king moves
               (= (first move) [4 0])
               (disj availability :K :Q) ;; black king moves
               (= (first move) [4 7])
               (disj availability :k :q)
               true
               availability
               )))))

(defn move->san[move]
  (if (nil? move)
     "No moves possible"
 (let [  [[x1 y1][x2 y2]]   move]
  
  ;; TODO: handle castling!!
  (str 
       (num->alpha x1)
       (inc y1)
       (num->alpha x2)
       (inc y2) 
       ) 
  )))



(defn make-move-aux[position move]
  (when false
    (println "make-move-aux: move is " move)
    ;; (println "position is " position)
    )

  (let [
        locs (:piece-locations position)

        piece (piece-at-location position (first move)) 
        my-diff (- (first (first move))
                   (first (second move)))
        castled? (when (and (is-king? piece)
                            (not (= 1 
                                    (Math/abs my-diff))))
                   (if (white-to-move? position)
                     ({2 :Q -2 :K} my-diff :oops)
                     ({2 :q -2 :k} my-diff :oops)
                     ))
       ;;  _ (if castled? (println "YOU CASTLED!!!!" castled?))
        promoted-piece (if (and (is-pawn? piece)
                                (location-at-eigth-rank? position
                                                         (second move)))
                         (choose-promoted-piece (:player-to-move position)) 
                         piece)

        new-locs 
        (if (= 4 (count move)) ;; castled 
          (-> locs 
              (dissoc (first move))
              (dissoc (nth move 2))
              (assoc (second move) promoted-piece)
              (assoc  (nth move 3) (get locs (nth move 2)))
              )
          ;; else
          (-> locs (dissoc (first move))
              (assoc (second move) promoted-piece)))
        ]
    (assert piece)
    (assert (friendly-square? position (first move)))
    ;; (update-position (flip-position (assoc position :piece-locations new-locs)))
    (-> position 
        (update-castling-availability move)
        (assoc :piece-locations new-locs)
        (assoc :identifier (move->san move))
        )
    ))


;; make a child node
(defn play-move[position move]
   (->     (make-move-aux position move)
       flip-position
  update-position))


(defn enemy-king-at-location?[position location]
  (let [piece (if (= :white (:player-to-move position)) :k :K)]
    (= piece (piece-at-location position location))))

(defn my-king-location[position]
  (let [piece (if (= :white (:player-to-move position)) :K :k)]
    (some #(when (= piece (second %)) (first %)) (:piece-locations position))))
;; (my-king-location starting-position)

;; (println test-position)
;; (println (position->string test-position))
;; (println (moves test-position))

;; (illegal-move? test-position [[0 0][0 1]])
(defn illegal-move?[position move]

  ;; Return true, if the move, when played, results in a position
  ;; where the current player's king is in check
   (let [resulting-position (make-move-aux position move)
         under-attack (locations-under-attack resulting-position)
         king-location (my-king-location resulting-position) 
         ]
         (when debug
           (println resulting-position)
          (println "under-attack=" under-attack)
           )
     (contains? under-attack king-location)
     ))
  ;;
  ;;   (some (fn[move2] (enemy-king-at-location? resulting-position
  ;;                                            (second move2)))
  ;;       (moves resulting-position))
  

(defn non-ranging-attack-locations[position location rules]
  "returns locations, including captures. For king and knight"
  (when debug (println "rules: " rules))
  (filter on-board? 
          (map (fn[rule] (apply rule location))
               rules)))

(defn ranging-attacks-in-direction[position location direction-fn]
  ;; return set of locations under attack using the direction fn
  ;; use reduce??
  (let [[x y] location ]
    (loop [locs-to-test (for [z (drop 1(range))]
                          (direction-fn x y z))
           accum []
           ]
      (when debug (println "ranging-attacks-in-direction: accum is" accum))
      (let [loc-to-test (first locs-to-test)]
        (cond 
          (off-board? loc-to-test)
          accum
          (occupied? position loc-to-test)
          (conj accum loc-to-test)
          true 
          (recur (next locs-to-test) (conj accum loc-to-test) )))
      )))

(defn ranging-attack-locations[position location rules]
  (when debug (println "Entering ranging-attack-locations"))
  (remove empty?
          (mapcat
            (partial ranging-attacks-in-direction position location)
            rules 
            )))


(defn enemy-color[position]
  (opposite-color
    (:player-to-move position))) 
(defn enemy-pieces[position]
  (filter (fn[[_ piece]]
            (=   (opposite-color (piece-color piece))
               (:player-to-move position))) 
          (:piece-locations position))
  )



(defn locations-under-attack[position]
  (when debug (println "Entering locations-under-attack, position is" 
                       position))
  (into (sorted-set)
        (mapcat 
          (fn[[loc piece]]  ;; destructure map entry
            (when debug (println "locations-under-attack piece is " piece))
            (let [
                  piece-info (pieces-info piece)]
              (when debug (println "loc is " loc  " piece is " piece " piece-info: " piece-info))
              ;;; better to use case???
              (cond (:ranging piece-info) ;; qr
                    (ranging-attack-locations position loc (:rules piece-info)) 
                    (is-pawn? piece) ;; p
                    (non-ranging-attack-locations 
                      position
                      loc 
                      (get pawn-attack-rules (enemy-color position)))
                    true ;; bnk
                    (non-ranging-attack-locations position loc (:rules piece-info)) 
                    ))) 
          (enemy-pieces position)
          )))

(defn under-attack? [position location]
  (contains? (:locations-under-attack position)
             location)

  )
(defn castling-available-black-queenside?[position]
   (and
       (= (piece-at-location position [0 7]) "r")
       (unoccupied? position [1 7])
       (unoccupied? position [2 7])
       (unoccupied? position  [3 7])
       (= (piece-at-location [4 7]) "k")
       ))

(defn castling-available-black-kingside?[position]
  (and (contains? (:castling-availability position) :k)
       (= (piece-at-location position [4 7]) "k")
       (unoccupied? position [5 7])
       (unoccupied? position [6 7])
       (= (piece-at-location position [7 7]) "r")
       )
  )

(defn castling-available-white-queenside?[position]
  (and (contains? (:castling-availability position) :Q)
       (= (piece-at-location position [0 0]) "R")
       (unoccupied? position [1 0])
       (unoccupied? position [2 0])
       (unoccupied? position  [3 0])
       (= (piece-at-location [4 0]) "K")
       ))


(defn castling-available-white-kingside?[position]
  (and (contains? (:castling-availability position) :K)
       (= (piece-at-location position [4 0]) "K")
       (unoccupied? position [6 0])
       (unoccupied? position [5 0])
       (= (piece-at-location [7 0]) "R")
       ))



(defn white-castling-moves[position]
  (remove nil?
          (vector 
            (when (castling-available-white-kingside? position)
              [[4 0] [6 0][7 0][5 0]])
            (when (castling-available-white-queenside? position)
              [[4 0] [2 0][0 0][3 0]])
            ))
  )
(defn black-castling-moves[position]
  (remove nil? (vector 
                 (when (castling-available-black-kingside? position)
                   [[4 7] [6 7][7 7] [5 7]])
                 (when (castling-available-black-queenside? position)
                   [[4 7] [2 7][0 7] [3 7]])
                 ) 
          ))

(defn white-to-move?[position]
  (= :white (:player-to-move position)))
(defn black-to-move?[position]
  (= :black (:player-to-move position)))

;; (castling-moves starting-position)
(defn castling-moves[position]
  (if (white-to-move? position)
    (white-castling-moves position)
    (black-castling-moves position))) 

(defn moves[position]
  (when debug
    (println "entering moves")
    (println "position is" position)
    )
  (sort
    (remove (fn[my-move] (cond
                           (illegal-move? position my-move) 
                           true
                           (is-king? (piece-at-location position (second my-move))) 
                           true
                           true
                           false
                           ))
            (concat
              (castling-moves position)
              (mapcat ;; loop through pieces
                      (fn[[loc piece]]  ;; destructure map entry
                        (when debug (println "piece is" piece))
                        (let [
                              piece-info (pieces-info piece)]
                          (when debug
                            (println "piece-info is" piece-info)
                            (println "piece is" piece))
                          (assert piece-info)
                          ;; use case ???
                          (cond (:ranging piece-info) ;; r,q, b
                                (ranging-moves position loc (:rules piece-info)) 
                                (is-pawn? piece) ;; p
                                (pawn-moves position loc) 
                                (is-knight? piece) ;; n 
                                (non-ranging-moves position loc (:rules piece-info)) 
                                (is-king? piece) ; k 
                                (:king-moves position)
                                true
                                (assert false)
                                ))) 
                      (filter (fn[[_ piece]]
                                (=   (piece-color piece)
                                   (:player-to-move position))) 
                              (:piece-locations position))
                      )))))

(defn checkmate?[position]
  (= :checkmate (:status position)))

(defn stalemate?[position]
  (= :stalemate (:status position)))


(defn string->position-and-print[x y]
  (let [position (string->position x y)]
    (println (position->string position))
    (println position)
    position))

(defn test-king-attack-sphere[]
  (string->position-and-print (str
                                "   K  \n"
                                "       \n"
                                "    k\n"
                                "\n \n \n \n "
                                )
                              :black))


;; (filter (partial unoccupied? enemy-position)
(declare play-game xboard-server)

(defn -main
  [& args]
  (cond
    (= "--demo" (first args))
    (play-game))
  true
  (do
    (println "Chess written in Clojure. by John Rothfield \nrthfield@sonic.net")
    (xboard-server)
    ))

(def starting-position
  (string->position starting-position-string :white))
(def testpos2
  (string->position (str 
                          "PP   k\n"
                          "K r     ")
                    :white))

(def test-position
  (string->position (str 
                      "        \n"
                      "   k     \n"
                      "  b    \n"
                      "       \n"
                      "R  QK  R\n"
                         )
                    :white))

;;(println "starting position\n" starting-position)
;;(println 
;; (string->position starting-position-string :black))

(defn is-capture?[position move]
  (if (piece-at-location position (second move))
    true
    false)
  )

(defn looks-like-castling-move[move]
  (= 4 (count move)))


(declare choose-move minimax)
(defn old-choose-move[position]
  ;; prefer castling for testing!!
  (let [favorite-moves 
        (filter #(if (looks-like-castling-move %) %)
                (:moves position))] 
    (or (when (not (empty? favorite-moves))
          (rand-nth favorite-moves))
        (some #(if (is-capture? position %) %) (:moves position))
        (rand-nth (:moves position))
        )))
;; (choose-move starting-position)
;; (choose-move (string->position-and-print "Rn k K" :white))
;; (println (join (map name (:castling-availability starting-position)) ))
;;  (->>  starting-position :castling-availability (map name)  join println))

(def char->num
  {\a 0 \b 1 \c 2 \d 3 \e 4 \f 5 \g 6 \h 7})

(def digit->num
  {\1 1 \2 2 \3 3 \4 4 \5 5 \6 6 \7 7 \8 8})

(defn san->move[position x]
 ;;  
  (cond 
    (and (= "e8g8" x)
             (is-king?
                          (piece-at-location position [4 8])))
         [[4 8] [6 8] [7 8] [5 8]]
    (and (= "e8c8" x)
             (is-king? 
                          (piece-at-location position [4 8])))
         [[4 8] [2 8] [0 8] [3 8]]

    (and (= "e1c1" x) ;; castle queenside
             (is-king?
              (piece-at-location position [4 0])))
         [[4 0] [2 0] [0 0] [3 0]]
    (and (= "e1g1" x) ;; castle kingside
             (is-king? 
             (piece-at-location position [4 0])))
         [[4 0] [6 0] [7 0] [5 0]]
        true
  (let [[x1 y1 x2 y2] (vec x)
        ]
  [ [ 
       (char->num x1)
       (dec  (digit->num y1))
     ]
   [
       (char->num x2)
       (dec  (digit->num y2))
   ]    
]
  )))
;;; (println (move->san [[0 0] [1 1]]))


(defn game-done?[position]
     (not (= :active (:status position)))
  )
 ;; (println (choose-move starting-position))
(defn xboard-server[]
  ;;; knights on Linux seems to work well!
  (println "rothfield's xboard server")
    (loop [line (read-line)
           position starting-position
           status :not-in-game
           ]
       ;;    (Thread/sleep 500)
      (cond (contains? #{"protover 2" "nopost" "hard" "st 20"}
                      line) 
              (recur (read-line) position status)
            (game-done? position)
            (do
            (println "Game is done: " (:status position) "!!")
              (recur (read-line) position status)
              )
            (= "xboard" line)
              (recur (read-line) position :in-game)
            (= "go" line)
              (let [move (choose-move position)]
              (println "move " (move->san move))
              (recur (read-line) (play-move position move) :in-game)
              )
            (= "new" line)
              (recur (read-line) starting-position :in-game)

            (re-matches #"[a-h][1-8][a-h][1-8].?" line)
            (let [your-move (san->move position line)
                  new-position (play-move position your-move)
                  my-move (when (not (game-done? new-position))
                            (choose-move new-position)) 
                  ] 
              (if (game-done? new-position)
                (do
            (println "Game is done: " (:status new-position) "!!")
              (recur (read-line) new-position status))
                (do
              (println "move " (move->san my-move))
              (recur (read-line)
                     (play-move new-position my-move) :in-game))))
            true
              (recur (read-line) position status))
            
      ))

;; set king to 0 for now
(def whites-piece-value-lookup {:p -1 :n -3 :b -3 :q -9 :r -5 
                          :k 0  :K 0   :P 1 :N 3 :B 3 :Q 9 :R 5})
(def blacks-piece-value-lookup {:k 0 :K 0 :p 1 :n 3 :b 3 :q 9 :r 5 
                                :P -1 :N -3 :B -3 :Q -9 :R -5})
(defn evaluate-position[position]
  (cond (checkmate? position)
        ({:w 10000 :b -10000} (:player-to-move position))
   (not (= :active (:status position))) 
      {:draw-by-insufficient-material 0
       :stalemate 0}
      ;; else 
        true
  (let [tbl (if (white-to-move? position) whites-piece-value-lookup
                blacks-piece-value-lookup)]
 (apply + (map #(get tbl %) 
   (map second (:piece-locations position)))))))
  
 (evaluate-position starting-position)



  
(defn play-game[]
  (let [noisy false]
    (println "\n\n\n")
    (loop [position starting-position
           move-ctr 0
           game-ctr 0]

      (when noisy (println move-ctr))
      (when true
        (println (position->string position))
        (println 
          (-> (map name (:castling-availability position)) (join "\n") println)
          )
        (Thread/sleep 1000)
        )

      (when (zero? (mod move-ctr 2000))
        ;;   (println ".")
        ;;   (println (position->string position))
        ;;     (Thread/sleep 1000)
        )
      (when debug
        (Thread/sleep 10000)
        (println move-ctr)
        (println position)
        )
      (cond 
        (> move-ctr 10000000)
        (do (println "Reached limit of " (str move-ctr) " iterations")
            (println (position->string position)))
        (= :checkmate (:status position))
        (do
          (println "Game" game-ctr ":   Moves:"  move-ctr (name (:status position)) "!")
          (println (position->string position))
          (Thread/sleep 10000)
          (recur  starting-position 0 (inc game-ctr))
          )
        (not (= :active (:status position)))
        (recur  starting-position 0 (inc game-ctr))
        true
        (recur (play-move position
                          (choose-move position)
                          )
               (inc move-ctr)
               (inc game-ctr))))))



;;;;;;;;;;;;;; TESTS ;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn test-king-cannot-move-to-a-location-under-attack[]
  (is (= 2 (count
             (moves (string->position-and-print "  K\nk   " :black))))))



(defn test-recognizes-checkmate[]
  (is (= []
         (moves (string->position-and-print (join "\n" [
                                                        "bbn"
                                                        "   "
                                                        "K r  "]) :white)))))

(defn test-king-moves[]
  (is (= '([[0 0] [1 1]] [[0 0] [1 0]] [[0 0] [0 1]])
         (moves (string->position "k" :black)))
      )
  (is (= 
        '([[0 0] [1 1]] [[0 0] [1 0]] [[0 0] [0 1]])
        (moves (string->position "kN" :black))))
  )
(defn test-non-ranging-moves[]
  (is (= 
        '([[0 0] [2 1]])
        (non-ranging-moves (string->position :n :black) [0 0] knight-rules)
        ))
  (is (= 
        '([[2 2] [4 3]] [[2 2] [0 3]] [[2 2] [4 1]] [[2 2] [0 3]])
        (non-ranging-moves 
          (string->position (join "\n" [
                                        "  n"
                                        "   "
                                        "   "]) :black)
          [2 2] knight-rules)
        )))



(defn test-knight-moves[]
  (is (= 8 
         (count (moves (string->position "  n\n   \n   " :black)))

         )))

(defn test-moves[]
  (is (= 37
         (count (moves (string->position "      P\nRNBQK" :white) )))
      ))

(defn test-position->string[]
  (is (= 
        starting-position-string
        (position->string
          (string->position starting-position-string :white)))))

(defn test-pawn-moves[]
  (is (= '("Pa2a3")
         (map (partial move->algebraic-notation starting-position)
              (pawn-moves starting-position [0 1])))))

(defn test-not-in-check[]
  (is (not (:in-check starting-position)))
  )
(defn test-in-check[]
  (is (:in-check 
        (string->position "rK" :white) )
      ))
(defn test-checkmated[]
  (is (= :checkmate
         (:status (string->position-and-print "PPPP\nK  r" :white)))
      ))
;; (moves (string->position "rK" :black) )
;;
;; (king-location (string->position "rK" :white))
(defn test-starting-moves[]
  (let [position starting-position]
    (map #(move->algebraic-notation position %) 
         (moves starting-position))))
(defn run-tests[]
  (test-moves)
  (test-not-in-check)
  (test-checkmated)
  (test-in-check)
  (test-king-moves)
  (test-king-cannot-move-to-a-location-under-attack)
  (test-pawn-moves)
  ;;(test-position->string)
  ;;(test-starting-moves)
  )
;;(run-tests)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(test-king-moves)
;;(test-starting-moves)
;;(test-king-cannot-move-to-a-location-under-attack)
;;  (test-in-check)
;;(println "running tests")
;;(run-tests)

(def MINIMAX-DEPTH 4)

(defn terminal-node?[position]
  (game-done? position)
 )


(defn heuristic-value-of-node[node]

   (evaluate-position node)
  )
;; (println (choose-move starting-position))
(declare minimax-aux)
(defn choose-move[position]
  (let [z (minimax-aux position 3 true)]
 (:identifier (last (sort-by :value (:children z)))
  ))
 ;; (map (fn[move] 
 ;; (minimax position 1))

;; (test-minimax)
;; (println  (:value (test-minimax)))
;;(println (map :value (test-minimax)))
;; (minimax starting-position 1 true)
  ;;         (heuristic-value-of-node starting-position)

(defn leaf-node?[node depth]
     (or (zero? depth) (terminal-node? node)))

(defn minimax-aux[position minimax-depth my-maximizing-player]
   (loop [node position
          depth minimax-depth
          maximizing-player my-maximizing-player]
     (if (leaf-node? node depth)
          { :identifier (:identifier node)
           :value  (if maximizing-player
                     (heuristic-value-of-node node)
                     (* -1 (heuristic-value-of-node node)))
                     }
        (let [children (map 
                      (fn[my-move] (minimax-aux 
                                        (play-move node my-move)
                                        (dec depth)
                                        (not maximizing-player)))
                          (moves node)) 
              ]
        { :identifier (:identifier node)
          :children children
          :value (apply (get {true max false min} maximizing-player)
                       (map :value children))
                       }
          ))))

(defn minimax[position]
  (minimax-aux position 4 true))

(defn test-minimax[]
  (let [position (string->position-and-print (str 
                                  "    \n"
                                  "P   \n"
                                  "   P \n"
                                  "P    \n" 
                                  "K    \n"
                                  "    \n"
                                  "k  p \n" 
                                  "   " 
                                  ) :white)]
    (pprint (minimax-aux position 1 true))
    (is (pos? 
           (:value (minimax-aux position 1 true))))
    (is (neg? 
           (:value (minimax-aux position 2 true))))
    
    ))
;; (test-minimax)
  ;;  if depth = 0 or node is a terminal node
   ;;     return the heuristic value of node
    ;;if maximizingPlayer
     ;;   bestValue := -∞
      ;;  for each child of node
       ;;     val := minimax(child, depth - 1, FALSE)
        ;;    bestValue := max(bestValue, val)
       ;; return bestValue
  ;;  else
   ;;     bestValue := +∞
    ;;    for each child of node
     ;;       val := minimax(child, depth - 1, TRUE)
      ;;      bestValue := min(bestValue, val)
       ;; return bestValue

