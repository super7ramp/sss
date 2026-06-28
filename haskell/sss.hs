-- Credit to https://web.archive.org/web/20201109101535/http://www.cse.chalmers.se/~algehed/blogpostsHTML/SAT.html

type Literal = Int
type Clause = [Literal]
type Problem = [Clause]
type Assignment = [Literal]

propagate :: Literal -> Problem -> Problem
propagate l p = [ filter (/= negate l) c | c <- p, l `notElem` c ]

solve :: Problem -> [Assignment]
solve [] = [[]]
solve ([]:p) = []
solve ((l:c):p) =
     [ l:assignment        | assignment <- solve (propagate l p) ]
  ++ [ negate l:assignment | assignment <- solve (propagate (negate l) (c:p)) ]

main :: IO ()
main = do
  putStrLn "Example: Trivial clauses"
  putStrLn "Input: (1 or 2) and (-2 or 3)"
  putStrLn (show (solve [[1, 2], [-2, 3]]))
