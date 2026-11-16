ALTER TABLE `anchor_process`
    DROP INDEX `blockchain_product`,
    ADD UNIQUE KEY `blockchain_product`
        (`blockchain_product`, `instance`, `task`, `tpbta_lane_key`);
