package org.freedesktop;
import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt64;
public final class Struct1 extends Struct
{
   @Position(0)
   public final UInt64 a;
   @Position(1)
   public final double b;
  public Struct1(UInt64 a, double b)
  {
   this.a = a;
   this.b = b;
  }
}
