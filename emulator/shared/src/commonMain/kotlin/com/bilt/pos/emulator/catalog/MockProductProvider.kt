package com.bilt.pos.emulator.catalog

/**
 * Fixed catalog spanning sub-dollar items to $500+, so every price band of
 * the payment flow (small-ticket, signature thresholds, large amounts) is one
 * tap away. Replace with a real provider when the emulator grows one.
 */
object MockProductProvider : ProductProvider {

    private val catalog = listOf(
        Product("SKU-0001", "Chewing Gum", 75, "Grocery"),
        Product("SKU-0002", "Banana", 35, "Grocery"),
        Product("SKU-0003", "Chocolate Bar", 185, "Grocery"),
        Product("SKU-0004", "Greeting Card", 250, "Misc"),
        Product("SKU-0005", "Bottled Water", 129, "Grocery"),
        Product("SKU-0006", "Coffee", 375, "Grocery"),
        Product("SKU-0007", "Notebook", 499, "Office"),
        Product("SKU-0008", "Sandwich", 849, "Grocery"),
        Product("SKU-0009", "Socks 3-Pack", 999, "Apparel"),
        Product("SKU-0010", "Umbrella", 1499, "Misc"),
        Product("SKU-0011", "Phone Case", 1999, "Electronics"),
        Product("SKU-0012", "T-Shirt", 2499, "Apparel"),
        Product("SKU-0013", "Desk Lamp", 3499, "Home"),
        Product("SKU-0014", "Backpack", 4999, "Apparel"),
        Product("SKU-0015", "Blender", 6499, "Home"),
        Product("SKU-0016", "Running Shoes", 7999, "Apparel"),
        Product("SKU-0017", "Headphones", 12999, "Electronics"),
        Product("SKU-0018", "E-Reader", 13999, "Electronics"),
        Product("SKU-0019", "Smartwatch", 19999, "Electronics"),
        Product("SKU-0020", "Robot Vacuum", 27999, "Home"),
        Product("SKU-0021", "Tablet", 32999, "Electronics"),
        Product("SKU-0022", "Espresso Machine", 44999, "Home"),
        Product("SKU-0023", "Game Console", 49999, "Electronics"),
        Product("SKU-0024", "4K Television", 54999, "Electronics"),
    )

    override fun products(): List<Product> = catalog
}
