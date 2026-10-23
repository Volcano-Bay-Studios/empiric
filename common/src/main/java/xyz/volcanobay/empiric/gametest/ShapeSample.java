package xyz.volcanobay.empiric.gametest;

import xyz.volcanobay.empiric.core.data.Data;
import xyz.volcanobay.empiric.core.data.SelfSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Values of an abstract type, registered as a polymorphic spec in {@link EmpiricTestModules}.
 */
@SelfSpec
public class ShapeSample {

    @Data
    public Shape main = new Circle(1);
    @Data
    public List<Shape> many = new ArrayList<>();

    public abstract static class Shape {
    }

    @SelfSpec
    public static class Circle extends Shape {

        @Data
        public int radius;

        public Circle() {
        }

        public Circle(int radius) {
            this.radius = radius;
        }
    }

    @SelfSpec
    public static class Square extends Shape {

        @Data
        public int side;

        public Square() {
        }

        public Square(int side) {
            this.side = side;
        }
    }
}
