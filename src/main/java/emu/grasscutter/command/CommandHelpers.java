package emu.grasscutter.command;

import emu.grasscutter.game.world.Position;

public final class CommandHelpers {
    private CommandHelpers() {}

    public static float parseRelative(String input, Float current) {
        if (input.contains("~")) {
            if (!input.equals("~")) {
                current += Float.parseFloat(input.replace("~", ""));
            }
        } else {
            current = Float.parseFloat(input);
        }
        return current;
    }

    public static Position parsePosition(
            String inputX, String inputY, String inputZ, Position curPos, Position curRot) {
        Position offset = new Position();
        Position target = new Position(curPos);
        if (inputX.contains("~")) {
            if (!inputX.equals("~")) {
                target.addX(Float.parseFloat(inputX.replace("~", "")));
            }
        } else if (inputX.contains("^")) {
            if (!inputX.equals("^")) {
                offset.setX(Float.parseFloat(inputX.replace("^", "")));
            }
        } else {
            target.setX(Float.parseFloat(inputX));
        }

        if (inputY.contains("~")) {
            if (!inputY.equals("~")) {
                target.addY(Float.parseFloat(inputY.replace("~", "")));
            }
        } else if (inputY.contains("^")) {
            if (!inputY.equals("^")) {
                offset.setY(Float.parseFloat(inputY.replace("^", "")));
            }
        } else {
            target.setY(Float.parseFloat(inputY));
        }

        if (inputZ.contains("~")) {
            if (!inputZ.equals("~")) {
                target.addZ(Float.parseFloat(inputZ.replace("~", "")));
            }
        } else if (inputZ.contains("^")) {
            if (!inputZ.equals("^")) {
                offset.setZ(Float.parseFloat(inputZ.replace("^", "")));
            }
        } else {
            target.setZ(Float.parseFloat(inputZ));
        }

        if (!offset.equal3d(Position.ZERO)) {
            return calculateOffset(target, curRot, offset);
        }
        return target;
    }

    public static Position calculateOffset(Position pos, Position rot, Position offset) {
        float angleZ = (float) Math.toRadians(rot.getY());
        float angleX = (float) Math.toRadians(rot.getY() + 90);

        return new Position(
                pos.getX()
                        + offset.getZ() * (float) Math.sin(angleZ)
                        + offset.getX() * (float) Math.sin(angleX),
                pos.getY() + offset.getY(),
                pos.getZ()
                        + offset.getZ() * (float) Math.cos(angleZ)
                        + offset.getX() * (float) Math.cos(angleX));
    }
}
