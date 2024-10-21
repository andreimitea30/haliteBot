import java.util.*;

public class MyBot {
    private static final int TRESHOLD = 6; // pragul de productie
    private static final int MAX_STRENGTH = 255; // forta maxima


    // returneaza locatia zonei detinute de bot in directia d
    private static Location getNeighbour(GameMap gameMap, Location location, Direction direction) {
        return gameMap.getLocation(location, direction);
    }

    // in functie de directia pe care se deplaseaza, returneaza lungimea sau latimea hartii
    private static int getLimits(Direction d, GameMap gameMap) {
        if (d == Direction.NORTH || d == Direction.SOUTH) {
            return gameMap.height;
        } else {
            return gameMap.width;
        }
    }

    // verifica daca o locatie este langa marginea zonei detinuta de bot
    private static boolean bordersMyZone(Location location, GameMap gameMap, int myID) {
        if (location.getSite().owner != myID) {
            for (Direction d : Direction.CARDINALS) {
                if (gameMap.getLocation(location, d).getSite().owner == myID) {
                    return true;
                }
            }
        }
        return false;
    }

    // verifica daca o locatie este la marginea zonei detinuta de bot
    private static boolean isBorderTile(Location location, GameMap gameMap, int myID) {
        if (location.getSite().owner != myID) {
            return false;
        }
        for (Direction d : Direction.CARDINALS) {
            if (gameMap.getLocation(location, d).getSite().owner != myID) {
                return true;
            }
        }
        return false;
    }

    // formula pentru scorul unei locatii in functie de productie si forta
    private static double getScore(Location location) {
        return TRESHOLD * location.getSite().production - location.getSite().strength;
    }

    // calculeaza forta unei locatii dupa un numar de pasi
    private static int calculateStrengthAfter(Location location, int steps) {
        return location.getSite().strength + steps * location.getSite().production;
    }

    // calculeaza cea mai buna directie imediata spre marginea zonei detinute de bot
    private static Direction getBestClosestBorder(Location location, GameMap gameMap, int myID) {
        Site current = location.getSite();
        double initialRatio;
        int bestDistance = Integer.MAX_VALUE;

        if (current.production > 0) {
            initialRatio = (double) (MAX_STRENGTH - current.strength) / current.production;
        } else {
            initialRatio = MAX_STRENGTH - current.strength;
        }

        Direction bestDirection = Direction.STILL;
        double bestRatio = initialRatio;

        for (Direction d : Direction.CARDINALS) {
            Location neighbour = getNeighbour(gameMap, location, d);
            Site neighbourSite = neighbour.getSite();
            int distance = 0;

            // Traverse until finding a non-owned tile or reaching map bounds
            while (neighbourSite.owner == myID && distance < getLimits(d, gameMap) / 2) {
                neighbour = getNeighbour(gameMap, neighbour, d);
                neighbourSite = neighbour.getSite();
                distance++;
            }

            // Only consider if the tile found is not owned by myID
            if (neighbourSite.owner != myID) {
                double ratio = neighbourSite.production > 0 ?
                        (double) (MAX_STRENGTH - neighbourSite.strength) / neighbourSite.production :
                        MAX_STRENGTH - neighbourSite.strength;

                // Check if the found enemy tile is a better option
                if (distance < bestDistance || (distance == bestDistance && ratio > bestRatio)) {
                    bestDistance = distance;
                    bestRatio = ratio;
                    bestDirection = d;
                }
            }
        }
        return bestDirection;
    }

    // returneaza mutarile posibile pentru o locatie dupa un numar de pasi, sustine retragerea pentru a nu fi cucerita
    private static ArrayList<Move> getBestMoveAfterSteps(Location location, int steps, GameMap gameMap, int myID, boolean[][] foundMoveForPiece) {
        ArrayList<Move> validMoves = new ArrayList<>();
        for (Direction d : Direction.CARDINALS) {
            Location neighbour = getNeighbour(gameMap, location, d);
            if (neighbour.getSite().owner == myID && !foundMoveForPiece[neighbour.getX()][neighbour.getY()]) {
                validMoves.add(new Move(neighbour, oppositeDirection(d)));
            }
        }
        validMoves.sort((move1, move2) -> {
            int move1AfterSteps = calculateStrengthAfter(move1.loc, steps);
            int move2AfterSteps = calculateStrengthAfter(move2.loc, steps);
            return move2AfterSteps - move1AfterSteps;
        });
        return validMoves;
    }

    // returneaza directia opusa
    private static Direction oppositeDirection(Direction d) {
        if (d == Direction.NORTH) {
            return Direction.SOUTH;
        } else if (d == Direction.SOUTH) {
            return Direction.NORTH;
        } else if (d == Direction.EAST) {
            return Direction.WEST;
        } else {
            return Direction.EAST;
        }
    }

    // incearca sa cucereasca o locatie
    private static ArrayList<Move> conquerMode(Location location, int steps, GameMap gameMap, int myID, boolean[][] foundMoveForPiece) {
        ArrayList<Move> moves = new ArrayList<>();
        ArrayList<Move> nearbyMoves = getBestMoveAfterSteps(location, steps, gameMap, myID, foundMoveForPiece);

        if (nearbyMoves.isEmpty()) {
            return moves;  // nu exista mutari posibile
        }

        // evalueaza succesul mutarilor posibile
        for (int count = 1; count <= 3; count++) {
            if (tryMoves(nearbyMoves, count, steps, location.getSite().strength, moves, gameMap, foundMoveForPiece)) {
                break;  // daca s-a gasit o combinatie de mutari care sa cucereasca locatia, oprim cautarea
            }
        }

        for (Move move : moves) {
            foundMoveForPiece[move.loc.getX()][move.loc.getY()] = true;
        }
        return moves;
    }

    // incearca mutarile valide pe rand pana cand se atinge forta tinta
    private static boolean tryMoves(ArrayList<Move> candidates, int count, int steps, int targetStrength, ArrayList<Move> resultMoves, GameMap gameMap, boolean[][] foundMoveForPiece) {
        int accumulatedStrength = 0;
        List<Move> selectedMoves = new ArrayList<>();

        for (int i = 0; i < Math.min(count, candidates.size()); i++) {
            Move move = candidates.get(i);
            accumulatedStrength += calculateStrengthAfter(move.loc, steps);
            selectedMoves.add(move);

            if (accumulatedStrength > targetStrength) {
                if (steps > 0) {
                    // daca s-a atins forta tinta, se incearca mutari ulterioare
                    ArrayList<Move> nextMoves = getBestMoveAfterSteps(move.loc, steps, gameMap, move.loc.getSite().owner, foundMoveForPiece);
                    if (tryMoves(nextMoves, count - i - 1, steps - 1, targetStrength, resultMoves, gameMap, foundMoveForPiece)) {
                        resultMoves.addAll(selectedMoves);
                        return true;  // mutare reusita
                    }
                } else {
                    resultMoves.addAll(selectedMoves);
                    return true;  // mutare reusita
                }
                return true;  // mutare reusita
            }
        }
        return false;  // mutare esuata
    }

    // returneaza scorul vecinilor unei locatii
    private static double getNeighbourScore(Location location, GameMap gameMap, int myID) {
        double score = 0;
        for (Direction d : Direction.CARDINALS) {
            Location neighbour = getNeighbour(gameMap, location, d);
            if (neighbour.getSite().owner != myID) {
                score += getScore(neighbour);
            }
        }
        return score;
    }

    // aplica mutarile daca sunt valide
    private static boolean applyMovesIfValid(Move bestMove, Move bestNeighbour, Location targetLoc, List<Move> moves, boolean[][] foundMoveForPiece) {
        int combinedStrength = calculateStrengthAfter(bestMove.loc, 1) + bestNeighbour.loc.getSite().strength;
        int targetStrength = targetLoc.getSite().strength;

        if (combinedStrength > targetStrength) {
            addMoveAndMarkAsMoved(bestMove, Direction.STILL, moves, foundMoveForPiece);
            addMoveAndMarkAsMoved(bestNeighbour, bestNeighbour.dir, moves, foundMoveForPiece);
            return true;
        }

        return false;
    }

    // functie care adauga o mutare in lista de mutari si o marcheaza ca fiind efectuata
    private static void addMoveAndMarkAsMoved(Move move, Direction direction, List<Move> moves, boolean[][] foundMoveForPiece) {
        moves.add(new Move(move.loc, direction));
        foundMoveForPiece[move.loc.getX()][move.loc.getY()] = true;
    }

    // incearca o alta abordare a strategiei
    private static boolean tryAlternativeStrategy(Location loc, GameMap gameMap, int myID, boolean[][] foundMoveForPiece, List<Move> moves) {
        ArrayList<Move> bestMoves = getBestMoveAfterSteps(loc, 1, gameMap, myID, foundMoveForPiece);
        if (bestMoves.isEmpty()) {
            return false;
        }

        Move bestMove = bestMoves.get(0);
        ArrayList<Move> bestNeighbours = getBestMoveAfterSteps(bestMove.loc, 0, gameMap, myID, foundMoveForPiece);
        if (bestNeighbours.isEmpty()) {
            return false;
        }

        Move bestNeighbour = bestNeighbours.get(0);

        if (applyMovesIfValid(bestMove, bestNeighbour, loc, moves, foundMoveForPiece)) {
            return true;
        }

        return false;
    }

    public static void main(String[] args) throws java.io.IOException {
        final InitPackage iPackage = Networking.getInit();
        final int myID = iPackage.myID;
        final GameMap gameMap = iPackage.map;
        boolean[][] foundMoveForPiece = new boolean[gameMap.width][gameMap.height]; // matricea de tiles mutate
        Networking.sendInit("Bombasticul");

        while (true) {
            List<Move> moves = new ArrayList<>();
            Networking.updateFrame(gameMap);
            for (int i = 0; i < gameMap.width; i++) {
                for (int j = 0; j < gameMap.height; j++) {
                    foundMoveForPiece[i][j] = false;
                }
            }

            // coada pentru viitoarele mutari, sortate dupa scorul vecinilor
            PriorityQueue<Location> scores = new PriorityQueue<>((Location location1, Location location2) -> {
                double score1 = getScore(location1) + getNeighbourScore(location1, gameMap, myID);
                double score2 = getScore(location2) + getNeighbourScore(location2, gameMap, myID);
                return Double.compare(score2, score1);
            });

            // parcurgem harta pentru a gasi locatiile de pe marginea zonei detinute de bot
            for (int y = 0; y < gameMap.height; y++) {
                for (int x = 0; x < gameMap.width; x++) {

                    final Location location = gameMap.getLocation(x, y);
                    // daca locatia este la marginea zonei detinute de bot, o adaugam in coada
                    if (bordersMyZone(location, gameMap, myID)) {
                        scores.add(location);
                    }
                }
            }
            if (!scores.isEmpty()) {
                do {
                    // scoatem locatia cu cel mai mare scor
                    Location location = scores.poll();

                    // incercam sa cucerim locatia
                    ArrayList<Move> movesForEdge = conquerMode(location, 0, gameMap, myID, foundMoveForPiece);
                    if (!movesForEdge.isEmpty()) {
                        moves.addAll(movesForEdge);
                        continue;
                    }

                    movesForEdge = conquerMode(location, 1, gameMap, myID, foundMoveForPiece);
                    if (!movesForEdge.isEmpty()) {
                        moves.addAll(movesForEdge);
                        continue;
                    }

                    // daca nu am gasit o solutie imediata sau pe un pas, incercam o strategie alternativa
                    if (tryAlternativeStrategy(location, gameMap, myID, foundMoveForPiece, moves)) {
                        continue;
                    }
                } while (!scores.isEmpty());
            }
            // parcurgem harta
            for (int y = 0; y < gameMap.height; y++) {
                for (int x = 0; x < gameMap.width; x++) {

                    // extragem locatia curenta si site-ul asociat
                    final Location location = gameMap.getLocation(x, y);
                    final Site site = location.getSite();

                    // luam in calcul doar locatiile detinute de noi
                    if (location.getSite().owner == myID) {
                        // daca piesa este marginala, verificam daca s-a putut gasi o mutare
                        boolean isBorder = isBorderTile(location, gameMap, myID);
                        if (!isBorder) {
                            // daca piesa nu este marginala, o mutam spre marginea zonei detinute de noi
                            // daca se indeplineste conditia de forta
                            if (site.strength > TRESHOLD * site.production) {
                                moves.add(new Move(location, getBestClosestBorder(location, gameMap, myID)));
                            } else {
                                moves.add(new Move(location, Direction.STILL));
                            }
                        } else {
                            // daca piesa este marginala, verificam daca s-a gasit o mutare
                            if (!foundMoveForPiece[location.getX()][location.getY()]) {
                                // daca nu s-a gasit o mutare, piesa va sta pe loc
                                moves.add(new Move(location, Direction.STILL));
                            }

                        }
                        // marcam piesa ca fiind mutata
                        int locationX = location.getX();
                        int locationY = location.getY();
                        if (!foundMoveForPiece[locationX][locationY]) {
                            foundMoveForPiece[locationX][locationY] = true;
                        }
                    }
                }
            }
            Networking.sendFrame(moves);
        }
    }
}
